/*
 *  Olvid for Android
 *  Copyright © 2019-2024 Olvid SAS
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

package io.olvid.messenger.discussion.location;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// automatically generated: json object returned by pelias reverse api
@JsonIgnoreProperties(ignoreUnknown = true)
public class PeliasReverseResponse {

	@JsonProperty("features")
	private List<FeaturesItem> features;

	@JsonProperty("geocoding")
	private Geocoding geocoding;

	@JsonProperty("bbox")
	private List<Double> bbox;

	@JsonProperty("type")
	private String type;

	public void setFeatures(List<FeaturesItem> features){
		this.features = features;
	}

	public List<FeaturesItem> getFeatures(){
		return features;
	}

	public void setGeocoding(Geocoding geocoding){
		this.geocoding = geocoding;
	}

	public Geocoding getGeocoding(){
		return geocoding;
	}

	public void setBbox(List<Double> bbox){
		this.bbox = bbox;
	}

	public List<Double> getBbox(){
		return bbox;
	}

	public void setType(String type){
		this.type = type;
	}

	public String getType(){
		return type;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Query{

		@JsonProperty("private")
		private boolean jsonMemberPrivate;

		@JsonProperty("point.lon")
		private double pointLon;

		@JsonProperty("size")
		private int size;

		@JsonProperty("querySize")
		private int querySize;

		@JsonProperty("layers")
		private List<String> layers;

		@JsonProperty("boundary.circle.lon")
		private double boundaryCircleLon;

		@JsonProperty("boundary.circle.lat")
		private double boundaryCircleLat;

		@JsonProperty("lang")
		private Lang lang;

		@JsonProperty("point.lat")
		private double pointLat;

		public void setJsonMemberPrivate(boolean jsonMemberPrivate){
			this.jsonMemberPrivate = jsonMemberPrivate;
		}

		public boolean isJsonMemberPrivate(){
			return jsonMemberPrivate;
		}

		public void setPointLon(double pointLon){
			this.pointLon = pointLon;
		}

		public double getPointLon(){
			return pointLon;
		}

		public void setSize(int size){
			this.size = size;
		}

		public int getSize(){
			return size;
		}

		public void setQuerySize(int querySize){
			this.querySize = querySize;
		}

		public int getQuerySize(){
			return querySize;
		}

		public void setLayers(List<String> layers){
			this.layers = layers;
		}

		public List<String> getLayers(){
			return layers;
		}

		public void setBoundaryCircleLon(double boundaryCircleLon){
			this.boundaryCircleLon = boundaryCircleLon;
		}

		public double getBoundaryCircleLon(){
			return boundaryCircleLon;
		}

		public void setBoundaryCircleLat(double boundaryCircleLat){
			this.boundaryCircleLat = boundaryCircleLat;
		}

		public double getBoundaryCircleLat(){
			return boundaryCircleLat;
		}

		public void setLang(Lang lang){
			this.lang = lang;
		}

		public Lang getLang(){
			return lang;
		}

		public void setPointLat(double pointLat){
			this.pointLat = pointLat;
		}

		public double getPointLat(){
			return pointLat;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Geometry{

		@JsonProperty("coordinates")
		private List<Double> coordinates;

		@JsonProperty("type")
		private String type;

		public void setCoordinates(List<Double> coordinates){
			this.coordinates = coordinates;
		}

		public List<Double> getCoordinates(){
			return coordinates;
		}

		public void setType(String type){
			this.type = type;
		}

		public String getType(){
			return type;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Geocoding{

		@JsonProperty("engine")
		private Engine engine;

		@JsonProperty("query")
		private Query query;

		@JsonProperty("attribution")
		private String attribution;

		@JsonProperty("version")
		private String version;

		@JsonProperty("timestamp")
		private long timestamp;

		public void setEngine(Engine engine){
			this.engine = engine;
		}

		public Engine getEngine(){
			return engine;
		}

		public void setQuery(Query query){
			this.query = query;
		}

		public Query getQuery(){
			return query;
		}

		public void setAttribution(String attribution){
			this.attribution = attribution;
		}

		public String getAttribution(){
			return attribution;
		}

		public void setVersion(String version){
			this.version = version;
		}

		public String getVersion(){
			return version;
		}

		public void setTimestamp(long timestamp){
			this.timestamp = timestamp;
		}

		public long getTimestamp(){
			return timestamp;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Lang{

		@JsonProperty("defaulted")
		private boolean defaulted;

		@JsonProperty("name")
		private String name;

		@JsonProperty("iso6391")
		private String iso6391;

		@JsonProperty("iso6393")
		private String iso6393;

		@JsonProperty("via")
		private String via;

		public void setDefaulted(boolean defaulted){
			this.defaulted = defaulted;
		}

		public boolean isDefaulted(){
			return defaulted;
		}

		public void setName(String name){
			this.name = name;
		}

		public String getName(){
			return name;
		}

		public void setIso6391(String iso6391){
			this.iso6391 = iso6391;
		}

		public String getIso6391(){
			return iso6391;
		}

		public void setIso6393(String iso6393){
			this.iso6393 = iso6393;
		}

		public String getIso6393(){
			return iso6393;
		}

		public void setVia(String via){
			this.via = via;
		}

		public String getVia(){
			return via;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class FeaturesItem{

		@JsonProperty("geometry")
		private Geometry geometry;

		@JsonProperty("type")
		private String type;

		@JsonProperty("properties")
		private Properties properties;

		public void setGeometry(Geometry geometry){
			this.geometry = geometry;
		}

		public Geometry getGeometry(){
			return geometry;
		}

		public void setType(String type){
			this.type = type;
		}

		public String getType(){
			return type;
		}

		public void setProperties(Properties properties){
			this.properties = properties;
		}

		public Properties getProperties(){
			return properties;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Engine{

		@JsonProperty("author")
		private String author;

		@JsonProperty("name")
		private String name;

		@JsonProperty("version")
		private String version;

		public void setAuthor(String author){
			this.author = author;
		}

		public String getAuthor(){
			return author;
		}

		public void setName(String name){
			this.name = name;
		}

		public String getName(){
			return name;
		}

		public void setVersion(String version){
			this.version = version;
		}

		public String getVersion(){
			return version;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Properties{

		@JsonProperty("country")
		private String country;

		@JsonProperty("macrocounty_gid")
		private String macrocountyGid;

		@JsonProperty("gid")
		private String gid;

		@JsonProperty("locality_gid")
		private String localityGid;

		@JsonProperty("distance")
		private double distance;

		@JsonProperty("macroregion_gid")
		private String macroregionGid;

		@JsonProperty("accuracy")
		private String accuracy;

		@JsonProperty("source")
		private String source;

		@JsonProperty("macroregion")
		private String macroregion;

		@JsonProperty("region_a")
		private String regionA;

		@JsonProperty("layer")
		private String layer;

		@JsonProperty("localadmin")
		private String localadmin;

		@JsonProperty("localadmin_gid")
		private String localadminGid;

		@JsonProperty("macrocounty")
		private String macrocounty;

		@JsonProperty("street")
		private String street;

		@JsonProperty("postalcode")
		private String postalcode;

		@JsonProperty("region_gid")
		private String regionGid;

		@JsonProperty("id")
		private String id;

		@JsonProperty("country_gid")
		private String countryGid;

		@JsonProperty("confidence")
		private double confidence;

		@JsonProperty("country_a")
		private String countryA;

		@JsonProperty("macroregion_a")
		private String macroregionA;

		@JsonProperty("locality")
		private String locality;

		@JsonProperty("label")
		private String label;

		@JsonProperty("country_code")
		private String countryCode;

		@JsonProperty("housenumber")
		private String housenumber;

		@JsonProperty("name")
		private String name;

		@JsonProperty("source_id")
		private String sourceId;

		@JsonProperty("region")
		private String region;

		public void setCountry(String country){
			this.country = country;
		}

		public String getCountry(){
			return country;
		}

		public void setMacrocountyGid(String macrocountyGid){
			this.macrocountyGid = macrocountyGid;
		}

		public String getMacrocountyGid(){
			return macrocountyGid;
		}

		public void setGid(String gid){
			this.gid = gid;
		}

		public String getGid(){
			return gid;
		}

		public void setLocalityGid(String localityGid){
			this.localityGid = localityGid;
		}

		public String getLocalityGid(){
			return localityGid;
		}

		public void setDistance(double distance){
			this.distance = distance;
		}

		public double getDistance(){
			return distance;
		}

		public void setMacroregionGid(String macroregionGid){
			this.macroregionGid = macroregionGid;
		}

		public String getMacroregionGid(){
			return macroregionGid;
		}

		public void setAccuracy(String accuracy){
			this.accuracy = accuracy;
		}

		public String getAccuracy(){
			return accuracy;
		}

		public void setSource(String source){
			this.source = source;
		}

		public String getSource(){
			return source;
		}

		public void setMacroregion(String macroregion){
			this.macroregion = macroregion;
		}

		public String getMacroregion(){
			return macroregion;
		}

		public void setRegionA(String regionA){
			this.regionA = regionA;
		}

		public String getRegionA(){
			return regionA;
		}

		public void setLayer(String layer){
			this.layer = layer;
		}

		public String getLayer(){
			return layer;
		}

		public void setLocaladmin(String localadmin){
			this.localadmin = localadmin;
		}

		public String getLocaladmin(){
			return localadmin;
		}

		public void setLocaladminGid(String localadminGid){
			this.localadminGid = localadminGid;
		}

		public String getLocaladminGid(){
			return localadminGid;
		}

		public void setMacrocounty(String macrocounty){
			this.macrocounty = macrocounty;
		}

		public String getMacrocounty(){
			return macrocounty;
		}

		public void setStreet(String street){
			this.street = street;
		}

		public String getStreet(){
			return street;
		}

		public void setPostalcode(String postalcode){
			this.postalcode = postalcode;
		}

		public String getPostalcode(){
			return postalcode;
		}

		public void setRegionGid(String regionGid){
			this.regionGid = regionGid;
		}

		public String getRegionGid(){
			return regionGid;
		}

		public void setId(String id){
			this.id = id;
		}

		public String getId(){
			return id;
		}

		public void setCountryGid(String countryGid){
			this.countryGid = countryGid;
		}

		public String getCountryGid(){
			return countryGid;
		}

		public void setConfidence(double confidence){
			this.confidence = confidence;
		}

		public double getConfidence(){
			return confidence;
		}

		public void setCountryA(String countryA){
			this.countryA = countryA;
		}

		public String getCountryA(){
			return countryA;
		}

		public void setMacroregionA(String macroregionA){
			this.macroregionA = macroregionA;
		}

		public String getMacroregionA(){
			return macroregionA;
		}

		public void setLocality(String locality){
			this.locality = locality;
		}

		public String getLocality(){
			return locality;
		}

		public void setLabel(String label){
			this.label = label;
		}

		public String getLabel(){
			return label;
		}

		public void setCountryCode(String countryCode){
			this.countryCode = countryCode;
		}

		public String getCountryCode(){
			return countryCode;
		}

		public void setHousenumber(String housenumber){
			this.housenumber = housenumber;
		}

		public String getHousenumber(){
			return housenumber;
		}

		public void setName(String name){
			this.name = name;
		}

		public String getName(){
			return name;
		}

		public void setSourceId(String sourceId){
			this.sourceId = sourceId;
		}

		public String getSourceId(){
			return sourceId;
		}

		public void setRegion(String region){
			this.region = region;
		}

		public String getRegion(){
			return region;
		}
	}
}