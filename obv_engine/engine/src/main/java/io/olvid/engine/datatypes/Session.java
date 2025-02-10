/*
 *  Olvid for Android
 *  Copyright © 2019-2025 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.engine.datatypes;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import io.olvid.engine.Logger;

public class Session implements Connection {
    final static ReentrantLock globalWriteLock = new ReentrantLock();
    private final static HashMap<String, List<Session>> sessionPool = new HashMap<>();
    private final static ReentrantLock sessionPoolLock = new ReentrantLock();

    private final Connection connection;
    private final Set<SessionCommitListener> sessionCommitListeners;
    private final String dbPath;
    private final boolean sessionIsForUpgradeTable;

    static {
        try {
            DriverManager.registerDriver((Driver) Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            Logger.x(e);
        }
    }

    private Session(String dbPath, String dbKey, boolean sessionIsForUpgradeTables) throws SQLException {
        if (dbPath == null) {
            throw new SQLException("dbPath is null, unable to create a Session.");
        }
        this.dbPath = dbPath;
        this.sessionCommitListeners = new LinkedHashSet<>();
        this.sessionIsForUpgradeTable = sessionIsForUpgradeTables;
        Properties properties = new Properties();
        properties.setProperty("secure_delete", "on");
        properties.setProperty("temp_store", "2");
        properties.setProperty("journal_mode", "WAL");
        properties.setProperty("busy_timeout", "10000"); // increase the db locked timeout as some queries may take more than 3s!
        if (dbKey != null) {
            properties.setProperty("password", dbKey);
        }
        if (sessionIsForUpgradeTables) {
            // No foreign keys and no autocommit, but legacy alter table to avoid renaming references when renaming table
            properties.setProperty("legacy_alter_table", "true");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath, properties);
        } else {
            properties.setProperty("foreign_keys", "true");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath, properties);
            this.connection.setAutoCommit(true);
        }
    }

    public static Session getSession(String dbPath, String dbKey) throws SQLException {
        Session session;

        sessionPoolLock.lock();
        List<Session> sessionList = sessionPool.get(dbPath);
        if ((sessionList == null) || (sessionList.isEmpty())) {
            sessionPoolLock.unlock();
            session = new Session(dbPath, dbKey, false);
        } else {
            session = sessionList.remove(0);
            sessionPoolLock.unlock();
        }
        return session;
    }

    public static Session getUpgradeTablesSession(String dbPath, String dbKey) throws SQLException {
        return new Session(dbPath, dbKey, true);
    }

    public void addSessionCommitListener(SessionCommitListener listener) {
        sessionCommitListeners.add(listener);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isInTransaction() throws SQLException {
        return !connection.getAutoCommit();
    }

    public void startTransaction() throws SQLException {
        globalWriteLock.lock();
        connection.setAutoCommit(false);
    }

    public static boolean databaseIsReadable(String dbPath, String dbKey) {
        try (Session session = new Session(dbPath, dbKey, true)) {
            try (Statement statement = session.createStatement()) {
                statement.execute("SELECT count(*) FROM sqlite_master;");
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void commit() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
            connection.setAutoCommit(true);
            globalWriteLock.unlock();
        }
        for (SessionCommitListener sessionCommitListener: sessionCommitListeners) {
            sessionCommitListener.wasCommitted();
        }
        sessionCommitListeners.clear();
    }

    @Override
    public void rollback() throws SQLException {
        if (!connection.getAutoCommit()) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
                sessionCommitListeners.clear();
            } finally {
                globalWriteLock.unlock();
            }
        } else {
            Logger.d("Calling rollback on an autoCommit Session.");
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        return new DeferrableStatement(connection.createStatement(), this);
    }

    @Override
    public PreparedStatement prepareStatement(String s) throws SQLException {
        return new DeferrablePreparedStatement(connection.prepareStatement(s), this);
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i) throws SQLException {
        return new DeferrablePreparedStatement(connection.prepareStatement(s, i), this);
    }

    @Override
    public void close() throws SQLException {
        if (!sessionCommitListeners.isEmpty() ) {
            Logger.e("This Session was not properly closed: some modifications were committed and the corresponding hooks have not been called.");
            for (SessionCommitListener sessionCommitListener: sessionCommitListeners) {
                Logger.e("  - Un-committed entity: " + sessionCommitListener.getClass());
            }
            sessionCommitListeners.clear();
            Logger.x(new Exception("Trace"));
        }
        if (!getAutoCommit()) {
            rollback();
        }
        if (sessionIsForUpgradeTable) {
            connection.close();
        } else {
            sessionPoolLock.lock();
            List<Session> sessionList = sessionPool.get(dbPath);
            if ((sessionList == null) || (sessionList.isEmpty())) {
                sessionList = new ArrayList<>();
                sessionPool.put(dbPath, sessionList);
            }
            sessionList.add(this);
            sessionPoolLock.unlock();
        }
    }

    @Override
    public void setAutoCommit(boolean b) throws SQLException {
        Logger.e("Calling setAutocommit on a Session is forbidden");
        throw new SQLException("Calling setAutocommit on a Session is forbidden");
    }


    // Proxy implementation of all the remaining Connection interface methods

    @Override
    public boolean getAutoCommit() throws SQLException {
        return connection.getAutoCommit();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return connection.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return connection.getMetaData();
    }

    @Override
    public void setReadOnly(boolean b) throws SQLException {
        connection.setReadOnly(b);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return connection.isReadOnly();
    }

    @Override
    public void setCatalog(String s) throws SQLException {
        connection.setCatalog(s);
    }

    @Override
    public String getCatalog() throws SQLException {
        return connection.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int i) throws SQLException {
        connection.setTransactionIsolation(i);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return connection.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return connection.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        connection.clearWarnings();
    }

    @Override
    public Statement createStatement(int i, int i1) throws SQLException {
        return connection.createStatement(i, i1);
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1) throws SQLException {
        return connection.prepareStatement(s, i, i1);
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1) throws SQLException {
        return connection.prepareCall(s, i, i1);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return connection.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        connection.setTypeMap(map);
    }

    @Override
    public void setHoldability(int i) throws SQLException {
        connection.setHoldability(i);
    }

    @Override
    public int getHoldability() throws SQLException {
        return connection.getHoldability();
    }

    @Override
    public CallableStatement prepareCall(String s) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public String nativeSQL(String s) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLException("Savepoints not implemented");
    }

    @Override
    public Savepoint setSavepoint(String s) throws SQLException {
        throw new SQLException("Savepoints not implemented");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLException("Savepoints not implemented");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLException("Savepoints not implemented");
    }

    @Override
    public Statement createStatement(int i, int i1, int i2) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1, int i2) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1, int i2) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public PreparedStatement prepareStatement(String s, int[] ints) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public PreparedStatement prepareStatement(String s, String[] strings) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public Clob createClob() throws SQLException {
        return connection.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return connection.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return connection.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return connection.createSQLXML();
    }

    @Override
    public boolean isValid(int i) throws SQLException {
        return connection.isValid(i);
    }

    @Override
    public void setClientInfo(String s, String s1) throws SQLClientInfoException {
        connection.setClientInfo(s, s1);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        connection.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String s) throws SQLException {
        return connection.getClientInfo(s);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return connection.getClientInfo();
    }

    @Override
    public Array createArrayOf(String s, Object[] objects) throws SQLException {
        return connection.createArrayOf(s, objects);
    }

    @Override
    public Struct createStruct(String s, Object[] objects) throws SQLException {
        return connection.createStruct(s, objects);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return connection.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return connection.isWrapperFor(iface);
    }

    @Override
    public void setSchema(String s) throws SQLException {
        connection.setSchema(s);
    }

    @Override
    public String getSchema() throws SQLException {
        return connection.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        connection.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int i) throws SQLException {
        connection.setNetworkTimeout(executor, i);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return connection.getNetworkTimeout();
    }
}




class DeferrableStatement implements Statement {
    private final Statement statement;
    private final Session session;

    DeferrableStatement(Statement statement, Session session) {
        this.statement = statement;
        this.session = session;
    }

    @Override
    public void close() throws SQLException {
        statement.close();
    }

    @Override
    public boolean execute(final String s) throws SQLException {
        if (session.getAutoCommit()) {
            try {
                Session.globalWriteLock.lock();
                return statement.execute(s);
            } finally {
                Session.globalWriteLock.unlock();
            }
        } else {
            return statement.execute(s);
        }
    }

    @Override
    public ResultSet executeQuery(String s) throws SQLException {
        return statement.executeQuery(s);
    }

    @Override
    public int executeUpdate(final String s) throws SQLException {
        if (session.getAutoCommit()) {
            try {
                Session.globalWriteLock.lock();
                return statement.executeUpdate(s);
            } finally {
                Session.globalWriteLock.unlock();
            }
        } else {
            return statement.executeUpdate(s);
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return statement.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int i) throws SQLException {
        statement.setMaxFieldSize(i);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return statement.getMaxRows();
    }

    @Override
    public void setMaxRows(int i) throws SQLException {
        statement.setMaxRows(i);
    }

    @Override
    public void setEscapeProcessing(boolean b) throws SQLException {
        statement.setEscapeProcessing(b);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return statement.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int i) throws SQLException {
        statement.setQueryTimeout(i);
    }

    @Override
    public void cancel() throws SQLException {
        statement.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return statement.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        statement.clearWarnings();
    }

    @Override
    public void setCursorName(String s) throws SQLException {
        statement.setCursorName(s);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return statement.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return statement.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return statement.getMoreResults();
    }

    @Override
    public void setFetchDirection(int i) throws SQLException {
        statement.setFetchDirection(i);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return statement.getFetchDirection();
    }

    @Override
    public void setFetchSize(int i) throws SQLException {
        statement.setFetchSize(i);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return statement.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return statement.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return statement.getResultSetType();
    }

    @Override
    public void addBatch(String s) throws SQLException {
        statement.addBatch(s);
    }

    @Override
    public void clearBatch() throws SQLException {
        statement.clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return statement.getConnection();
    }

    @Override
    public boolean getMoreResults(int i) throws SQLException {
        return statement.getMoreResults(i);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return statement.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String s, int i) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public int executeUpdate(String s, int[] ints) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public int executeUpdate(String s, String[] strings) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public boolean execute(String s, int i) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public boolean execute(String s, int[] ints) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public boolean execute(String s, String[] strings) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return statement.getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return statement.isClosed();
    }

    @Override
    public void setPoolable(boolean b) throws SQLException {
        statement.setPoolable(b);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return statement.isPoolable();
    }

    @Override
    public <T> T unwrap(Class<T> aClass) throws SQLException {
        return statement.unwrap(aClass);
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return statement.isWrapperFor(aClass);
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        statement.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return statement.isCloseOnCompletion();
    }
}



class DeferrablePreparedStatement implements PreparedStatement {
    private final PreparedStatement statement;
    private final Session session;

    DeferrablePreparedStatement(PreparedStatement statement, Session session) {
        this.statement = statement;
        this.session = session;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return statement.executeQuery();
    }

    @Override
    public int executeUpdate() throws SQLException {
        if (session.getAutoCommit()) {
            try {
                Session.globalWriteLock.lock();
                return statement.executeUpdate();
            } finally {
                Session.globalWriteLock.unlock();
            }
        } else {
            return statement.executeUpdate();
        }
    }

    @Override
    public boolean execute() throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public void close() throws SQLException {
        statement.close();
    }

    @Override
    public void setNull(int i, int i1) throws SQLException {
        statement.setNull(i, i1);
    }

    @Override
    public void setBoolean(int i, boolean b) throws SQLException {
        statement.setBoolean(i, b);
    }

    @Override
    public void setByte(int i, byte b) throws SQLException {
        statement.setByte(i, b);
    }

    @Override
    public void setShort(int i, short i1) throws SQLException {
        statement.setShort(i, i1);
    }

    @Override
    public void setInt(int i, int i1) throws SQLException {
        statement.setInt(i, i1);
    }

    @Override
    public void setLong(int i, long l) throws SQLException {
        statement.setLong(i, l);
    }

    @Override
    public void setFloat(int i, float v) throws SQLException {
        statement.setFloat(i, v);
    }

    @Override
    public void setDouble(int i, double v) throws SQLException {
        statement.setDouble(i, v);
    }

    @Override
    public void setBigDecimal(int i, BigDecimal bigDecimal) throws SQLException {
        statement.setBigDecimal(i, bigDecimal);
    }

    @Override
    public void setString(int i, String s) throws SQLException {
        statement.setString(i, s);
    }

    @Override
    public void setBytes(int i, byte[] bytes) throws SQLException {
        statement.setBytes(i, bytes);
    }

    @Override
    public void setDate(int i, Date date) throws SQLException {
        statement.setDate(i, date);
    }

    @Override
    public void setTime(int i, Time time) throws SQLException {
        statement.setTime(i, time);
    }

    @Override
    public void setTimestamp(int i, Timestamp timestamp) throws SQLException {
        statement.setTimestamp(i, timestamp);
    }

    @Override
    public void setAsciiStream(int i, InputStream inputStream, int i1) throws SQLException {
        statement.setAsciiStream(i, inputStream, i1);
    }

    @Deprecated
    @Override
    public void setUnicodeStream(int i, InputStream inputStream, int i1) throws SQLException {
        statement.setUnicodeStream(i, inputStream, i1);
    }

    @Override
    public void setBinaryStream(int i, InputStream inputStream, int i1) throws SQLException {
        statement.setBinaryStream(i, inputStream, i1);
    }

    @Override
    public void clearParameters() throws SQLException {
        statement.clearParameters();
    }

    @Override
    public void setObject(int i, Object o, int i1) throws SQLException {
        statement.setObject(i, o, i1);
    }

    @Override
    public void setObject(int i, Object o) throws SQLException {
        statement.setObject(i, o);
    }

    @Override
    public void addBatch() throws SQLException {
        statement.addBatch();
    }

    @Override
    public void setCharacterStream(int i, Reader reader, int i1) throws SQLException {
        statement.setCharacterStream(i, reader, i1);
    }

    @Override
    public void setRef(int i, Ref ref) throws SQLException {
        statement.setRef(i, ref);
    }

    @Override
    public void setBlob(int i, Blob blob) throws SQLException {
        statement.setBlob(i, blob);
    }

    @Override
    public void setClob(int i, Clob clob) throws SQLException {
        statement.setClob(i, clob);
    }

    @Override
    public void setArray(int i, Array array) throws SQLException {
        statement.setArray(i, array);
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return statement.getMetaData();
    }

    @Override
    public void setDate(int i, Date date, Calendar calendar) throws SQLException {
        statement.setDate(i, date, calendar);
    }

    @Override
    public void setTime(int i, Time time, Calendar calendar) throws SQLException {
        statement.setTime(i, time, calendar);
    }

    @Override
    public void setTimestamp(int i, Timestamp timestamp, Calendar calendar) throws SQLException {
        statement.setTimestamp(i, timestamp, calendar);
    }

    @Override
    public void setNull(int i, int i1, String s) throws SQLException {
        statement.setNull(i, i1, s);
    }

    @Override
    public void setURL(int i, URL url) throws SQLException {
        statement.setURL(i, url);
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return statement.getParameterMetaData();
    }

    @Override
    public void setRowId(int i, RowId rowId) throws SQLException {
        statement.setRowId(i, rowId);
    }

    @Override
    public void setNString(int i, String s) throws SQLException {
        statement.setNString(i, s);
    }

    @Override
    public void setNCharacterStream(int i, Reader reader, long l) throws SQLException {
        statement.setNCharacterStream(i, reader, l);
    }

    @Override
    public void setNClob(int i, NClob nClob) throws SQLException {
        statement.setNClob(i, nClob);
    }

    @Override
    public void setClob(int i, Reader reader, long l) throws SQLException {
        statement.setClob(i, reader, l);
    }

    @Override
    public void setBlob(int i, InputStream inputStream, long l) throws SQLException {
        statement.setBlob(i, inputStream, l);
    }

    @Override
    public void setNClob(int i, Reader reader, long l) throws SQLException {
        statement.setNClob(i, reader, l);
    }

    @Override
    public void setSQLXML(int i, SQLXML sqlxml) throws SQLException {
        statement.setSQLXML(i, sqlxml);
    }

    @Override
    public void setObject(int i, Object o, int i1, int i2) throws SQLException {
        statement.setObject(i, o, i1, i2);
    }

    @Override
    public void setAsciiStream(int i, InputStream inputStream, long l) throws SQLException {
        statement.setAsciiStream(i, inputStream, l);
    }

    @Override
    public void setBinaryStream(int i, InputStream inputStream, long l) throws SQLException {
        statement.setBinaryStream(i, inputStream, l);
    }

    @Override
    public void setCharacterStream(int i, Reader reader, long l) throws SQLException {
        statement.setCharacterStream(i, reader, l);
    }

    @Override
    public void setAsciiStream(int i, InputStream inputStream) throws SQLException {
        statement.setAsciiStream(i, inputStream);
    }

    @Override
    public void setBinaryStream(int i, InputStream inputStream) throws SQLException {
        statement.setBinaryStream(i, inputStream);
    }

    @Override
    public void setCharacterStream(int i, Reader reader) throws SQLException {
        statement.setCharacterStream(i, reader);
    }

    @Override
    public void setNCharacterStream(int i, Reader reader) throws SQLException {
        statement.setNCharacterStream(i, reader);
    }

    @Override
    public void setClob(int i, Reader reader) throws SQLException {
        statement.setClob(i, reader);
    }

    @Override
    public void setBlob(int i, InputStream inputStream) throws SQLException {
        statement.setBlob(i, inputStream);
    }

    @Override
    public void setNClob(int i, Reader reader) throws SQLException {
        statement.setNClob(i, reader);
    }

    @Override
    public ResultSet executeQuery(String s) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public int executeUpdate(String s) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return statement.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int i) throws SQLException {
        statement.setMaxFieldSize(i);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return statement.getMaxRows();
    }

    @Override
    public void setMaxRows(int i) throws SQLException {
        statement.setMaxRows(i);
    }

    @Override
    public void setEscapeProcessing(boolean b) throws SQLException {
        statement.setEscapeProcessing(b);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return statement.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int i) throws SQLException {
        statement.setQueryTimeout(i);
    }

    @Override
    public void cancel() throws SQLException {
        statement.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return statement.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        statement.clearWarnings();
    }

    @Override
    public void setCursorName(String s) throws SQLException {
        statement.setCursorName(s);
    }

    @Override
    public boolean execute(String s) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return statement.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return statement.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return statement.getMoreResults();
    }

    @Override
    public void setFetchDirection(int i) throws SQLException {
        statement.setFetchDirection(i);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return statement.getFetchDirection();
    }

    @Override
    public void setFetchSize(int i) throws SQLException {
        statement.setFetchSize(i);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return statement.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return statement.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return statement.getResultSetType();
    }

    @Override
    public void addBatch(String s) throws SQLException {
        statement.addBatch(s);
    }

    @Override
    public void clearBatch() throws SQLException {
        statement.clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return statement.getConnection();
    }

    @Override
    public boolean getMoreResults(int i) throws SQLException {
        return statement.getMoreResults(i);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return statement.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String s, int i) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public int executeUpdate(String s, int[] ints) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public int executeUpdate(String s, String[] strings) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public boolean execute(String s, int i) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public boolean execute(String s, int[] ints) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public boolean execute(String s, String[] strings) throws SQLException {
        throw new SQLException("Not implemented");
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return 0;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return statement.isClosed();
    }

    @Override
    public void setPoolable(boolean b) throws SQLException {

    }

    @Override
    public boolean isPoolable() throws SQLException {
        return statement.isPoolable();
    }

    @Override
    public <T> T unwrap(Class<T> aClass) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return statement.isWrapperFor(aClass);
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        statement.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return statement.isCloseOnCompletion();
    }
}