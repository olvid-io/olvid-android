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

package io.olvid.engine.engine.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Objects;

import io.olvid.engine.datatypes.ObvBase64;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonIdentityDetails {
    public static final String FORMAT_STRING_FIRST_LAST = "%f %l";
    public static final String FORMAT_STRING_FIRST_LAST_COMPANY = "%f %l (%c)";
    public static final String FORMAT_STRING_FIRST_LAST_POSITION_COMPANY = "%f %l (%p @ %c)";
    public static final String FORMAT_STRING_LAST_FIRST = "%l %f";
    public static final String FORMAT_STRING_LAST_FIRST_COMPANY = "%l %f (%c)";
    public static final String FORMAT_STRING_LAST_FIRST_POSITION_COMPANY = "%l %f (%p @ %c)";
    public static final String FORMAT_STRING_FOR_SEARCH = "%f %l %p %c";



    String firstName;
    String lastName;
    String company;
    String position;
    String signedUserDetails; // this is a JWT, non null when the identity is managed by a keycloak server
    HashMap<String, String> customFields;

    public JsonIdentityDetails() {
    }

    public JsonIdentityDetails(String firstName, String lastName, String company, String position) {
        this.firstName = nullOrTrim(firstName);
        this.lastName = nullOrTrim(lastName);
        this.company = nullOrTrim(company);
        this.position = nullOrTrim(position);
    }

    @JsonProperty("first_name")
    public String getFirstName() {
        return firstName;
    }

    @JsonProperty("first_name")
    public void setFirstName(String firstName) {
        this.firstName = nullOrTrim(firstName);
    }

    @JsonProperty("last_name")
    public String getLastName() {
        return lastName;
    }

    @JsonProperty("last_name")
    public void setLastName(String lastName) {
        this.lastName = nullOrTrim(lastName);
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = nullOrTrim(company);
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = nullOrTrim(position);
    }

    @JsonProperty("custom_fields")
    public HashMap<String, String> getCustomFields() {
        return customFields;
    }

    @JsonProperty("custom_fields")
    public void setCustomFields(HashMap<String, String> customFields) {
        this.customFields = customFields;
    }

    @JsonProperty("signed_user_details")
    public String getSignedUserDetails() {
        return signedUserDetails;
    }

    @JsonProperty("signed_user_details")
    public void setSignedUserDetails(String signedUserDetails) {
        this.signedUserDetails = signedUserDetails;
    }

    private static String nullOrTrim(String in) {
        if (in == null) {
            return null;
        }
        String out = in.trim();
        if (out.length() == 0) {
            return null;
        }
        return out;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return firstName == null && lastName == null;
    }


    @JsonIgnore
    public String formatDisplayName(String contactDisplayNameFormat, boolean uppercaseLastName) {
        String displayName;
        switch (contactDisplayNameFormat) {
            case FORMAT_STRING_FIRST_LAST_COMPANY: {
                displayName = joinNames(firstName, lastName, false, uppercaseLastName);
                if (company != null) {
                    displayName += " (" + company + ")";
                }
                break;
            }
            case FORMAT_STRING_FIRST_LAST_POSITION_COMPANY: {
                displayName = joinNames(firstName, lastName, false, uppercaseLastName);
                String posComp = joinCompany(position, company);
                if (posComp != null) {
                    displayName += " (" + posComp + ")";
                }
                break;
            }
            case FORMAT_STRING_LAST_FIRST: {
                displayName = joinNames(firstName, lastName, true, uppercaseLastName);
                break;
            }
            case FORMAT_STRING_LAST_FIRST_COMPANY: {
                displayName = joinNames(firstName, lastName, true, uppercaseLastName);
                if (company != null) {
                    displayName += " (" + company + ")";
                }
                break;
            }
            case FORMAT_STRING_LAST_FIRST_POSITION_COMPANY: {
                displayName = joinNames(firstName, lastName, true, uppercaseLastName);
                String posComp = joinCompany(position, company);
                if (posComp != null) {
                    displayName += " (" + posComp + ")";
                }
                break;
            }
            case FORMAT_STRING_FOR_SEARCH: {
                displayName = joinNames(firstName, lastName, false, false);
                String posComp = joinCompany(position, company);
                if (posComp != null) {
                    displayName += " " + posComp;
                }
                break;
            }
            case FORMAT_STRING_FIRST_LAST:
            default: {
                displayName = joinNames(firstName, lastName, false, uppercaseLastName);
                break;
            }
        }
        return displayName;
    }


    @JsonIgnore
    public String formatFirstAndLastName(String format, boolean uppercaseLastName) {
        switch (format) {
            case FORMAT_STRING_LAST_FIRST:
            case FORMAT_STRING_LAST_FIRST_COMPANY:
            case FORMAT_STRING_LAST_FIRST_POSITION_COMPANY:
                return joinNames(firstName, lastName, true, uppercaseLastName);
            case FORMAT_STRING_FIRST_LAST:
            case FORMAT_STRING_FIRST_LAST_COMPANY:
            case FORMAT_STRING_FIRST_LAST_POSITION_COMPANY:
            default:
                return joinNames(firstName, lastName, false, uppercaseLastName);
        }
    }

    @JsonIgnore
    @Nullable
    public String formatPositionAndCompany(@SuppressWarnings("unused") String format) {
        // for now, format is not used, but it may become necessary if new formats are added
        return joinCompany(position, company);
    }

    public static String joinNames(String firstName, String lastName, boolean lastFirst, boolean uppercaseLast) {
        if (lastName == null) {
            if (firstName == null) {
                return "";
            }
            return firstName;
        }
        if (uppercaseLast) {
            lastName = lastName.toUpperCase();
        }

        if (firstName == null) {
            return lastName;
        }

        if (lastFirst) {
            return lastName + " " + firstName;
        } else {
            return firstName + " " + lastName;
        }
    }

    private static String joinCompany(String position, String company) {
        if (position == null) {
            return company;
        }
        if (company == null) {
            return position;
        }
        return position + " @ " + company;
    }


    // equals does not compare signedUserDetails. It only checks whether these details are null/non-null
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JsonIdentityDetails)) {
            return false;
        }
        JsonIdentityDetails other = (JsonIdentityDetails) obj;
        if (!(Objects.equals(firstName, other.firstName))) {
            return false;
        }
        if (!(Objects.equals(lastName, other.lastName))) {
            return false;
        }
        if (!(Objects.equals(company, other.company))) {
            return false;
        }
        if (!(Objects.equals(position, other.position))) {
            return false;
        }
        if ((signedUserDetails == null && other.signedUserDetails != null) || (signedUserDetails != null && other.signedUserDetails == null)) {
            return false;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        if (!Objects.equals(getSignatureKid(objectMapper, signedUserDetails), getSignatureKid(objectMapper, other.signedUserDetails))) {
            return false;
        }
        return Objects.equals(customFields, other.customFields);
    }

    @JsonIgnore
    public boolean fieldsAreTheSame(JsonIdentityDetails other) {
        if (!(Objects.equals(firstName, other.firstName))) {
            return false;
        }
        if (!(Objects.equals(lastName, other.lastName))) {
            return false;
        }
        if (!(Objects.equals(company, other.company))) {
            return false;
        }
        if (!(Objects.equals(position, other.position))) {
            return false;
        }
        return Objects.equals(customFields, other.customFields);
    }

    @JsonIgnore
    public boolean firstAndLastNamesAreTheSame(JsonIdentityDetails other) {
        return Objects.equals(firstName, other.firstName) && Objects.equals(lastName, other.lastName);
    }


    private static String getSignatureKid(ObjectMapper objectMapper, String signature) {
        if (signature != null) {
            int pos = signature.indexOf('.');
            if (pos > 0) {
                String headerString = signature.substring(0, pos);
                try {
                    HashMap<String, String> header = objectMapper.readValue(ObvBase64.decode(headerString), new TypeReference<>() {});
                    return header.get("kid");
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
        return null;
    }
}
