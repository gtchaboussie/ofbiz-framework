/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

package org.apache.ofbiz.party.contact

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.GenericValue

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Create a PartyContactMech
 */
Map createPartyContactMech() {
    Map result = success()
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    GenericValue newValue = makeValue('PartyContactMech', parameters)

    //check if the contact mech infostring is already existing if so, do not create a new one
    List<GenericValue> partyContactMechs = from('PartyAndContactMech')
            .where(partyId: parameters.partyId,
                    infoString: parameters.infoString,
                    contactMechTypeId: parameters.contactMechTypeId)
            .filterByDate()
            .queryList()
    for (partyContactMech in partyContactMechs) {
        GenericValue contactMechType = from('ContactMechType')
                .where(contactMechTypeId: partyContactMech.contactMechTypeId)
                .cache().queryOne()
        if (contactMechType.hasTable == 'N') {
            parameters.infoString == partyContactMech.infoString
            logInfo "ContactMechId: ${partyContactMech.contactMechId} already exists with value: " +
                    "${partyContactMech.infoString} for party: ${partyContactMech.partyId} and ContactMechTypeId: ${partyContactMech.contactMechTypeId}"
            result.contactMechId = partyContactMech.contactMechId
            return result
        }
    }

    if (newValue.contactMechId) {
        Map serviceResult = run service: 'createContactMech', with: parameters
        newValue.contactMechId = serviceResult.contactMechId
    }
    logInfo "Creating a PartyContactMech with id: ${newValue.contactMechId}"
    newValue.fromDate = UtilDateTime.nowTimestamp()
    newValue.create()
    result.contactMechId = newValue.contactMechId
    return result
}

/**
 * Update a PartyContactMech
 */
Map updatePartyContactMech() {
    Map result = success()
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    GenericValue partyContactMechMap = makeValue('PartyContactMech').setPKFields(parameters)
    GenericValue oldPartyContactMech = from('PartyContactMech')
            .where(partyContactMechMap)
            .filterByDate()
            .queryFirst()
    if (!oldPartyContactMech) {
        return error(label('PartyUiLabels', 'PartyCannotUpdateContactBecauseNotWithSpecifiedParty'))
    }
    GenericValue newPartyContactMech = makeValue('PartyContactMech', parameters)
    if (!parameters.newContactMechId) {
        Map serviceResult = run service: 'updateContactMech', with: parameters
        newPartyContactMech.contactMechId = serviceResult.contactMechId
    } else {
        newPartyContactMech.contactMechId = parameters.newContactMechId
        logInfo "Using supplied new contact mech id: ${newPartyContactMech.contactMechId}"
    }

    // if new contactMech is created, deprecate old value and propage purposes
    if (newPartyContactMech.contactMechId != parameters.contactMechId) {
        newPartyContactMech.fromDate = UtilDateTime.nowTimestamp()
        newPartyContactMech.create()
        oldPartyContactMech.thruDate = newPartyContactMech.fromDate
        oldPartyContactMech.store()

        List partyContactMechPurposes = from('PartyContactMechPurpose')
                .where(partyContactMechMap)
                .filterByDate()
                .queryList()
        for (partyContactMechPurpose in partyContactMechPurposes) {
            GenericValue newPartyContactMechPurpose = [*: partyContactMechPurpose]
            partyContactMechPurpose.thruDate = newPartyContactMech.fromDate
            partyContactMechPurpose.store()

            partyContactMechPurpose.contactMechId = newPartyContactMech.contactMechId
            if (from('PartyContactMechPurpose')
                    .where(partyId: partyContactMechPurpose.partyId,
                            contactMechPurposeTypeId: partyContactMechPurpose.contactMechPurposeTypeId,
                            contactMechId: newPartyContactMechPurpose.contactMechId)
                    .queryCount() == 0) {
                newPartyContactMechPurpose.create()
            }
        }
        logInfo "Setting id to result: ${newPartyContactMech.contactMechId}"
        result.contactMechId = newPartyContactMech.contactMechId
    } else {
        String extension = oldPartyContactMech.extension
        oldPartyContactMech.setNonPKFields(parameters)
        if (parameters.extension != extension) {
            oldPartyContactMech.thruDate = null
        }
        oldPartyContactMech.store()
        logInfo "Setting id to result: ${oldPartyContactMech.contactMechId}"
        result.contactMechId = oldPartyContactMech.contactMechId
    }
    return result
}

/**
 * Delete a PartyContactMech
 */
Map deletePartyContactMech() {
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    GenericValue partyContactMechMap = makeValue('PartyContactMech').setPKFields(parameters)
    GenericValue partyContactMech = from('PartyContactMech')
            .where(partyContactMechMap)
            .filterByDate()
            .queryFirst()
    if (partyContactMech) {
        return error(UtilProperties.getMessage('PartyUiLabels', 'PartyContactMechNotFoundCannotDelete', locale))
    }
    partyContactMech.thruDate = UtilDateTime.nowTimestamp()
    partyContactMech.store()
    return success()
}

Map createPartyPostalAddress() {
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    if (parameters.latitude && parameters.longitude) {
        Map geoRes = run service: 'createGeoPoint', with: parameters
        parameters.geoPointId = geoRes.geoPointId
    }
    Map postalAddrRes = run service: 'createPostalAddress', with: parameters
    run service: 'createPartyContactMech', with: [*: parameters,
                                                  contactMechId: postalAddrRes.contactMechId,
                                                  contactMechTypeId: 'POSTAL_ADDRESS'
    ]
    return success(contactMechId: postalAddrRes.contactMechId)
}

Map updatePartyPostalAddress() {
    GenericValue newPartyContactMech = makeValue('PartyContactMech', parameters)
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    if (parameters.latitude && parameters.longitude) {
        Map geoRes = run service: 'createGeoPoint', with: parameters
        parameters.geoPointId = geoRes.geoPointId
    }
    Map updateRes = run service: 'updatePostalAddress', with: [parameters]
    newPartyContactMech.contactMechId = updateRes.contactMechId
    logInfo "Copied id to updatePartyContactMechMap: ${newPartyContactMech.contactMechId}"

    run service: 'updatePartyContactMech', with: [*: parameters,
                                                  contactMechId: newPartyContactMech.contactMechId,
                                                  contactMechTypeId: 'POSTAL_ADDRESS']
    return success(contactMechId: newPartyContactMech.contactMechId)
}


Map createPartyTelecomNumber() {
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    Map postalAddrRes = run service: 'createTelecomNumber', with: parameters
    run service: 'createPartyContactMech', with: [*: parameters,
                                                  contactMechId: postalAddrRes.contactMechId,
                                                  contactMechTypeId: 'TELECOM_NUMBER'
    ]
    return success(contactMechId: postalAddrRes.contactMechId)
}

Map updatePartyTelecomNumber() {
    GenericValue newPartyContactMech = makeValue('PartyContactMech', parameters)
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    Map updateRes = run service: 'updateTelecomNumber', with: [parameters]
    newPartyContactMech.contactMechId = updateRes.contactMechId
    logInfo "Copied id to updatePartyContactMechMap: ${newPartyContactMech.contactMechId}"

    run service: 'updatePartyContactMech', with: [*: parameters,
                                                  contactMechId: newPartyContactMech.contactMechId,
                                                  contactMechTypeId: 'TELECOM_NUMBER']
    return success(contactMechId: newPartyContactMech.contactMechId)
}

Map createPartyEmailAddress() {
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    String emailAddress = parameters.emailAddress
    if (!UtilValidate.isEmail(emailAddress)) {
        return error(label('PartyUiLabels', 'PartyEmailAddressNotFormattedCorrectly'))
    }

    GenericValue existingMail = from('PartyAndContactMech')
            .where(partyId: parameters.partyId,
                    contactMechTypeId: 'EMAIL_ADDRESS',
                    infoString: (parameters.emailAddress as String).toUpperCase() // TODO passer par un _ic en performFind
            )
            .filterByDate()
            .queryFirst()
    if (existingMail) {
        return (success(contactMechId: existingMail.contactMechId))
    }
    Map result = run service: 'createPartyContactMech', with: [*: parameters,
                                                               infoString: emailAddress,
                                                               contactMechTypeId: 'EMAIL_ADDRESS']

    return success(label('PartyUiLabels', 'PartyEmailAddressSuccessfullyCreated'), [contactMechId: result.contactMechId])
}

Map updatePartyEmailAddress() {
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    String emailAddress = parameters.emailAddress
    if (!UtilValidate.isEmail(emailAddress)) {
        return error(label('PartyUiLabels', 'PartyEmailAddressNotFormattedCorrectly'))
    }
    Map result = run service: 'updatePartyContactMech', with: [*: parameters,
                                                               infoString: emailAddress,
                                                               contactMechTypeId: 'EMAIL_ADDRESS']
    return success(label('PartyUiLabels', 'PartyEmailAddressSuccessfullyUpdated'), [contactMechId: result.contactMechId])
}

Map findPartyFromEmailAddress() {
    Map result = success()
    String caseInsensitive = parameters.caseInsensitive ?:
            UtilProperties.getPropertyValue('general.properties', 'mail.address.caseInsensitive', 'N')
    Map input = [filterByDate: 'Y',
                 entityName: 'PartyContactDetailByPurpose',
                 inputFields: [infoString: parameters.address,
                               infoString_ic: caseInsensitive,
                               contactMechPurposeTypeId: 'PRIMARY_EMAIL']
    ]
    if (!parameters.fromDate) {
        input.filterByDate = 'Y'
    } else {
        input.filterByDateValue = parameters.fromDate
    }
    Map searchResult = run service: 'performFind', with: input
    if (!searchResult.item) {
        input.entityName = 'PartyAndContactMech'
        input.inputFields.contactMechPurposeTypeId = null
        searchResult = run service: 'performFind', with: input
    }

    if (searchResult.item) {
        result.partyId = searchResult.item.partyId
        result.contactMechId = searchResult.item.contactMechId
    }
    return result
}

Map findPartyFromTelephone() {
    Map result = success()
    List<GenericValue> contactMechs = from('PartyAndContactMech')
            .where(contactMechTypeId: 'TELECOM_NUMBER')
            .queryList()
    String dash = '-'
    String emptyString = ''
    String inputTelno = (parameters.telno as String).replaceAll(dash, emptyString)

    GenericValue relevantContactMech = contactMechs.find { contactMech ->
        inputTelno == (contactMech?.tnContactNumber as String).replace(dash, emptyString) ||
                inputTelno == contactMech?.tnAreaCode + (contactMech?.tnContactNumber as String).replace(dash, emptyString) ||
                inputTelno == contactMech?.tnCountryCode + contactMech?.tnAreaCode + (contactMech?.tnContactNumber as String).replace(dash, emptyString) ||
                inputTelno == '+' + contactMech?.tnCountryCode + contactMech?.tnAreaCode + (contactMech?.tnContactNumber as String).replace(dash, emptyString)
    }

    if (relevantContactMech) {
        result.partyId = relevantContactMech.partyId
        result.contactMechId = relevantContactMech.contactMechId
    }
    return result
}

Map createPostalAddressAndPurposes() {
    if (parameters.roleTypeId) {
        if (from('PartyRole').where(parameters).queryCount() == 0) {
            return error(label('PartyUiLabels', 'PartyRoleTypeNotFoundForTheParty', [roleTypeId: parameters.roleTypeId]))
        }
    }
    Map createResult = run service: 'createPartyPostalAddress', with: parameters
    String newContactMechId = createResult.contactMechId

    if (!parameters.setShippingPurpose && !parameters.setBillingPurpose) {
        return success(contactMechId: newContactMechId)
    }

    if (parameters.setShippingPurpose = 'Y') {
        // Expire existing
        List<GenericValue> currentShippings = from('PartyContactMechPurpose')
                .where(partyId: parameters.partyId,
                        contactMechPurposeTypeId: 'SHIPPING_LOCATION')
                .queryList()
        for (currentShipping in currentShippings) {
            run service: 'expirePartyContactMechPurpose', with: [*: currentShipping]
        }
        run service: 'createPartyContactMechPurpose', with: [*: parameters,
                                                             partyId: parameters.partyId,
                                                             contactMechPurposeTypeId: 'SHIPPING_LOCATION']
        run service: 'setPartyProfileDefaults', with: [*: parameters,
                                                       defaultShipAddr: parameters.contactMechId,
                                                       partyId: userLogin.partyId]
    }
    if (parameters.setBillingPurpose = 'Y') {
        List<GenericValue> currentShippings = from('PartyContactMechPurpose')
                .where(partyId: parameters.partyId,
                        contactMechPurposeTypeId: 'BILLING_LOCATION')
                .queryList()
        for (currentShipping in currentShippings) {
            run service: 'expirePartyContactMechPurpose', with: [*: currentShipping]
        }
        run service: 'createPartyContactMechPurpose', with: [*: parameters,
                                                             partyId: parameters.partyId,
                                                             contactMechPurposeTypeId: 'BILLING_LOCATION']
        run service: 'setPartyProfileDefaults', with: [*: parameters,
                                                       defaultBillAddr: parameters.contactMechId,
                                                       partyId: userLogin.partyId]
    }
    return success()
}

Map updatePostalAddressAndPurposes() {

}

/*

    <simple-method method-name="updatePostalAddressAndPurposes" short-description="Update postal address, purposes and set them defaults" login-required="false">
        <entity-one entity-name="PartyProfileDefault" value-field="partyProfileDefault">
            <field-map field-name="partyId" from-field="userLogin.partyId"/>
            <field-map field-name="productStoreId" from-field="parameters.productStoreId"/>
        </entity-one>
        <if>
            <condition>
                <or>
                    <if-compare-field field="parameters.contactMechId" operator="equals" to-field="partyProfileDefault.defaultBillAddr"/>
                    <if-compare-field field="parameters.contactMechId" operator="equals" to-field="partyProfileDefault.defaultShipAddr"/>
                </or>
            </condition>
            <then>
                <if-compare-field field="partyProfileDefault.defaultBillAddr" operator="not-equals" to-field="partyProfileDefault.defaultShipAddr">
                    <call-service service-name="updatePartyPostalAddress" in-map-name="parameters">
                        <result-to-field result-name="contactMechId" field="parameters.contactMechId"/>
                        <result-to-result result-name="contactMechId"/>
                    </call-service>
                <else>
                    <set-service-fields service-name="updatePostalAddress" map="parameters" to-map="updatePostalAddressMap"/>
                    <call-service service-name="updatePostalAddress" in-map-name="updatePostalAddressMap">
                        <default-message resource="PartyUiLabels" property="PartyPostalAddressSuccessfullyUpdated"/>
                        <result-to-field result-name="contactMechId" field="parameters.newContactMechId"/>
                        <result-to-result result-name="contactMechId"/>
                    </call-service>

                    <if-compare-field field="parameters.contactMechId" operator="not-equals" to-field="parameters.newContactMechId">
                        <set-service-fields service-name="createPartyContactMech" map="parameters" to-map="createPartyContactMechMap"/>
                        <set field="createPartyContactMechMap.contactMechId" from-field="parameters.newContactMechId"/>
                        <set field="createPartyContactMechMap.contactMechTypeId" value="POSTAL_ADDRESS"/>
                        <call-service service-name="createPartyContactMech" in-map-name="createPartyContactMechMap" break-on-error="true">
                            <default-message resource="PartyUiLabels" property="PartyPostalAddressSuccessfullyCreated"/>
                        </call-service>
                    </if-compare-field>
                    <set field="parameters.contactMechId" from-field="parameters.newContactMechId"/>
                </else>
                </if-compare-field>
            </then>
            <else>
                <call-service service-name="updatePartyPostalAddress" in-map-name="parameters">
                    <result-to-field result-name="contactMechId" field="parameters.contactMechId"/>
                    <result-to-result result-name="contactMechId"/>
                </call-service>
            </else>
        </if>
        <!-- Setting the purposes -->
        <if>
            <condition>
                <or>
                    <not><if-empty field="parameters.setShippingPurpose"/></not>
                    <not><if-empty field="parameters.setBillingPurpose"/></not>
                </or>
            </condition>
            <then>
                <if-compare field="parameters.setShippingPurpose" operator="equals" value="Y">
                    <entity-and entity-name="PartyContactMechPurpose" list="pcmpShipList" filter-by-date="true">
                        <field-map field-name="partyId" from-field="userLogin.partyId"/>
                        <field-map field-name="contactMechId" from-field="parameters.contactMechId"/>
                        <field-map field-name="contactMechPurposeTypeId" value="SHIPPING_LOCATION"/>
                    </entity-and>
                    <!-- If purpose is not exists then create -->
                    <if-empty field="pcmpShipList">
                        <set-service-fields service-name="createPartyContactMechPurpose" map="parameters" to-map="serviceContext"/>
                        <set field="serviceContext.partyId" from-field="userLogin.partyId"/>

                        <entity-and entity-name="PartyContactMechPurpose" list="pcmpList" filter-by-date="true">
                            <field-map field-name="partyId" from-field="userLogin.partyId"/>
                            <field-map field-name="contactMechPurposeTypeId" value="SHIPPING_LOCATION"/>
                        </entity-and>
                        <iterate list="pcmpList" entry="pcmp">
                            <set-service-fields service-name="expirePartyContactMechPurpose" map="pcmp" to-map="serviceInMap"/>
                            <call-service service-name="expirePartyContactMechPurpose" in-map-name="serviceInMap"/>
                            <clear-field field="serviceInMap"/>
                        </iterate>
                        <set field="serviceContext.contactMechPurposeTypeId" value="SHIPPING_LOCATION"/>
                        <call-service service-name="createPartyContactMechPurpose" in-map-name="serviceContext"/>
                        <clear-field field="pcmpList"/>
                        <clear-field field="serviceContext"/>
                    </if-empty>

                    <set-service-fields service-name="setPartyProfileDefaults" map="parameters" to-map="partyProfileDefaultsCtx"/>
                    <set field="partyProfileDefaultsCtx.defaultShipAddr" from-field="parameters.contactMechId"/>
                    <set field="partyProfileDefaultsCtx.partyId" from-field="userLogin.partyId"/>
                    <call-service service-name="setPartyProfileDefaults" in-map-name="partyProfileDefaultsCtx"/>
                </if-compare>
                <if-compare field="parameters.setBillingPurpose" operator="equals" value="Y">
                    <entity-and entity-name="PartyContactMechPurpose" list="pcmpBillList" filter-by-date="true">
                        <field-map field-name="partyId" from-field="userLogin.partyId"/>
                        <field-map field-name="contactMechId" from-field="parameters.contactMechId"/>
                        <field-map field-name="contactMechPurposeTypeId" value="BILLING_LOCATION"/>
                    </entity-and>
                    <!-- If purpose is not exists then create -->
                    <if-empty field="pcmpBillList">
                        <set-service-fields service-name="createPartyContactMechPurpose" map="parameters" to-map="serviceContext"/>
                        <set field="serviceContext.partyId" from-field="userLogin.partyId"/>

                        <entity-and entity-name="PartyContactMechPurpose" list="pcmpList" filter-by-date="true">
                            <field-map field-name="partyId" from-field="userLogin.partyId"/>
                            <field-map field-name="contactMechPurposeTypeId" value="BILLING_LOCATION"/>
                        </entity-and>
                        <iterate list="pcmpList" entry="pcmp">
                            <set-service-fields service-name="expirePartyContactMechPurpose" map="pcmp" to-map="serviceInMap"/>
                            <call-service service-name="expirePartyContactMechPurpose" in-map-name="serviceInMap"/>
                        </iterate>
                        <set field="serviceContext.contactMechPurposeTypeId" value="BILLING_LOCATION"/>
                        <call-service service-name="createPartyContactMechPurpose" in-map-name="serviceContext"/>
                    </if-empty>

                    <set-service-fields service-name="setPartyProfileDefaults" map="parameters" to-map="partyProfileDefaultsCtx"/>
                    <set field="partyProfileDefaultsCtx.defaultBillAddr" from-field="parameters.contactMechId"/>
                    <set field="partyProfileDefaultsCtx.partyId" from-field="userLogin.partyId"/>
                    <call-service service-name="setPartyProfileDefaults" in-map-name="partyProfileDefaultsCtx"/>
                </if-compare>
            </then>
        </if>
    </simple-method>

    <simple-method method-name="updateContactMechAndPurposes" short-description="Update postal address, telecom number and purposes">
        <set-service-fields service-name="updatePostalAddressAndPurposes" map="parameters" to-map="updatePostalAddressAndPurposesCtx"/>
        <call-service service-name="updatePostalAddressAndPurposes" in-map-name="updatePostalAddressAndPurposesCtx">
            <result-to-result result-name="contactMechId"/>
        </call-service>

        <if-not-empty field="parameters.phoneContactMechId">
            <set-service-fields service-name="updatePartyTelecomNumber" map="parameters" to-map="updatePartyTelecomNumberCtx"/>
            <set field="updatePartyTelecomNumberCtx.contactMechId" from-field="parameters.phoneContactMechId"/>
            <call-service service-name="updatePartyTelecomNumber" in-map-name="updatePartyTelecomNumberCtx"/>
        </if-not-empty>
    </simple-method>

    <simple-method method-name="createUpdatePartyEmailAddress" short-description="Create and update email address" login-required="false">
        <if-empty field="parameters.contactMechId">
            <set-service-fields service-name="createPartyEmailAddress" map="parameters" to-map="emailAddressContext"/>
            <if-empty field="parameters.partyId">
                <set field="emailAddressContext.partyId" from-field="userLogin.partyId"/>
            </if-empty>
            <call-service service-name="createPartyEmailAddress" in-map-name="emailAddressContext">
                <result-to-field result-name="contactMechId" field="contactMechId"/>
            </call-service>
            <log level="info" message="Email Contact Created emailContactMechId is ${contactMechId}"></log>
        <else>
            <set-service-fields service-name="updatePartyEmailAddress" map="parameters" to-map="emailAddressContext"/>
            <call-service service-name="updatePartyEmailAddress" in-map-name="emailAddressContext">
                <result-to-field result-name="contactMechId" field="contactMechId"/>
            </call-service>
            <log level="info" message="Email Contact updated emailContactMechId is ${contactMechId}"></log>
        </else>
        </if-empty>
        <entity-one entity-name="ContactMech" value-field="contactMech"/>
        <field-to-result field="contactMech.infoString" result-name="emailAddress"/>
        <field-to-result field="contactMechId"/>
    </simple-method>

    <simple-method method-name="createUpdatePartyTelecomNumber" short-description="Create and update phone number" login-required="false">
        <if-empty field="parameters.contactMechId">
            <set-service-fields service-name="createPartyTelecomNumber" map="parameters" to-map="phoneContext"/>
            <call-service service-name="createPartyTelecomNumber" in-map-name="phoneContext">
                <result-to-field result-name="contactMechId" field="contactMechId"/>
            </call-service>
            <log level="info" message="Phone Contact created phoneContactMechId is ${contactMechId}"/>
        <else>
            <set-service-fields service-name="updatePartyTelecomNumber" map="parameters" to-map="phoneContext"/>
            <call-service service-name="updatePartyTelecomNumber" in-map-name="phoneContext">
                <result-to-field result-name="contactMechId" field="contactMechId"/>
            </call-service>
            <log level="info" message="Phone Contact updated phoneContactMechId is ${contactMechId}"/>
        </else>
        </if-empty>
        <field-to-result field="contactMechId"/>
    </simple-method>

    <simple-method method-name="createUpdatePartyPostalAddress" short-description="Create or update postal address" login-required="false">
        <if-empty field="parameters.contactMechId">
            <set-service-fields service-name="createPartyPostalAddress" map="parameters" to-map="postalAddressContext"/>
            <call-service service-name="createPartyPostalAddress" in-map-name="postalAddressContext">
                <result-to-field result-name="contactMechId" field="contactMechId"/>
            </call-service>
            <log level="info" message="Postal address created, contactMechId is ${contactMechId}"></log>
            <else>
                <set-service-fields service-name="updatePartyPostalAddress" map="parameters" to-map="postalAddressContext"/>
                <call-service service-name="updatePartyPostalAddress" in-map-name="postalAddressContext">
                    <result-to-field result-name="contactMechId" field="contactMechId"/>
                </call-service>
                <log level="info" message="Postal address updated, contactMechId is ${contactMechId}"></log>
            </else>
        </if-empty>
        <field-to-result field="contactMechId"/>
    </simple-method>

 */