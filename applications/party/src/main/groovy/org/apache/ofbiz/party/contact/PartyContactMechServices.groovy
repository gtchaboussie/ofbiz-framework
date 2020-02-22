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
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityFunction
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.entity.util.EntityUtilProperties
import org.apache.ofbiz.service.ServiceUtil

Map createPartyContactMech() {
    Map result = success()

    GenericValue newValue = makeValue("PartyContactMech")
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }

    // check if the contact mech infostring is already existing if so, do not create a new one
    List partyAndContactMechs = from("PartyAndContactMech")
            .where("partyId", parameters.partyId)
            .filterByDate()
            .queryList()
    for (GenericValue partyAndContactMech : partyAndContactMechs) {
        GenericValue contactMechType = from("ContactMechType")
                .where("contactMechTypeId", partyAndContactMech.contactMechTypeId)
                .queryOne()
        if ("N".equals(contactMechType.hasTable)
                && parameters.infoString == partyAndContactMech.infoString
                && parameters.contactMechTypeId == partyAndContactMech.contactMechTypeId) {
            logInfo("ContactMechId: ${partyAndContactMech.contactMechId} already exists with value: ${partyAndContactMech.infoString} for party: ${parameters.partyId} and ContactMechTypeId: ${partyAndContactMech.contactMechTypeId}")
            result.contactMechId = partyAndContactMech.contactMechId
            return result
        }
    }

    if (!parameters.contactMechId) {
        Map createContactMechResult = run service: "createContactMech", with: [*: parameters]
        if (!ServiceUtil.isSuccess(createContactMechResult)) return createContactMechResult
        newValue.contactMechId = createContactMechResult.contactMechId
        logInfo("ContactMech created")
        logInfo("Creating a PartyContactMech with id: ${newValue.contactMechId}")
    } else {
        newValue.contactMechId = parameters.contactMechId
        logInfo("Creating a PartyContactMech with id: ${parameters.contactMechId}")
    }
    newValue.partyId = parameters.partyId
    result.contactMechId = newValue.contactMechId
    newValue.setNonPKFields(parameters)
    newValue.fromDate = UtilDateTime.nowTimestamp()
    newValue.create()
    return result
}

Map updatePartyContactMech() {
    Map result = success()

    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }

    GenericValue partyContactMechMap = makeValue("PartyContactMech")
    partyContactMechMap.setPKFields(parameters)
    List partyContactMechs = from("PartyContactMech").where(partyContactMechMap).queryList()
    List validPartyContactMechs = EntityUtil.filterByDate(partyContactMechs)
    GenericValue partyContactMech = EntityUtil.getFirst(validPartyContactMechs)
    if (!partyContactMech) {
        return error(UtilProperties.getMessage("PartyUiLabels", "PartyCannotUpdateContactBecauseNotWithSpecifiedParty", locale))
    }

    GenericValue newPartyContactMech = (GenericValue) partyContactMech.clone()

    // If we already have a new contactMechId don"t update ContactMech
    if (!parameters.newContactMechId) {
        Map updateContactMechResult = run service: "updateContactMech", with: [*: parameters]
        if (!ServiceUtil.isSuccess(updateContactMechResult)) return updateContactMechResult
        // default-message PartyContactMechanismSuccessfullyUpdated
        result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyContactMechanismSuccessfullyUpdated", locale)
        newPartyContactMech.contactMechId = updateContactMechResult.contactMechId
    } else {
        newPartyContactMech.contactMechId = parameters.newContactMechId
        logInfo("Using supplied new contact mech id: ${newPartyContactMech.contactMechId}")
    }

    if (newPartyContactMech.contactMechId != parameters.contactMechId) {
        newPartyContactMech.setNonPKFields(parameters)
        newPartyContactMech.fromDate = UtilDateTime.nowTimestamp()
        partyContactMech.thruDate = UtilDateTime.nowTimestamp()
        partyContactMech.store()
        newPartyContactMech.create()
        List partyContactMechPurposes = partyContactMech.getRelated("PartyContactMechPurpose", null, null, false)
        partyContactMechPurposes = EntityUtil.filterByDate(partyContactMechPurposes)
        Map purposeMap = [:]
        for (GenericValue partyContactMechPurposeOld : partyContactMechPurposes) {
            GenericValue partyContactMechPurpose = (GenericValue) partyContactMechPurposeOld.clone()
            partyContactMechPurposeOld.thruDate = UtilDateTime.nowTimestamp()
            partyContactMechPurposeOld.store()

            partyContactMechPurpose.contactMechId = newPartyContactMech.contactMechId
            purposeMap.partyId = partyContactMechPurpose.partyId
            purposeMap.contactMechPurposeTypeId = partyContactMechPurpose.contactMechPurposeTypeId
            purposeMap.contactMechId = partyContactMechPurpose.contactMechId
            List purposeResult = from("PartyContactMechPurpose").where(purposeMap).queryList()

            if (!purposeResult) {
                partyContactMechPurpose.create()
            }
        }
        logInfo("Setting id to result: ${newPartyContactMech.contactMechId}")
        result.contactMechId = newPartyContactMech.contactMechId
    } else {
        String extension = partyContactMech.extension
        partyContactMech.setNonPKFields(parameters)
        if (parameters.extension != extension) {
            partyContactMech.thruDate = null // value="" en minilang => null
        }
        partyContactMech.store()
        logInfo("Setting id to result: ${partyContactMech.contactMechId}")
        result.contactMechId = partyContactMech.contactMechId
    }
    return result
}

Map deletePartyContactMech() {
    Map result = success()

    GenericValue newPartyContactMech = makeValue("PartyContactMech")
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }

    GenericValue partyContactMechMap = makeValue("PartyContactMech")
    partyContactMechMap.setPKFields(parameters)
    List partyContactMechs = from("PartyContactMech").where(partyContactMechMap).queryList()
    List validPartyContactMechs = EntityUtil.filterByDate(partyContactMechs)
    GenericValue partyContactMech = EntityUtil.getFirst(validPartyContactMechs)
    if (!partyContactMech) {
        return error(UtilProperties.getMessage("PartyUiLabels", "PartyContactMechNotFoundCannotDelete", locale))
    }
    partyContactMech.thruDate = UtilDateTime.nowTimestamp()
    partyContactMech.store()
    return result
}

Map createPartyPostalAddress() {
    Map result = success()
    Map newPartyContactMech = [:]

    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    if (parameters.latitude) {
        if (parameters.longitude) {
            Map createGeoPointResult = run service: "createGeoPoint", with: [*: parameters]
            if (!ServiceUtil.isSuccess(createGeoPointResult)) return createGeoPointResult
            parameters.geoPointId = createGeoPointResult.geoPointId
            // check-errors
        }
    }
    Map createPostalAddressResult = run service: "createPostalAddress", with: [*: parameters]
    if (!ServiceUtil.isSuccess(createPostalAddressResult)) return createPostalAddressResult
    newPartyContactMech.contactMechId = createPostalAddressResult.contactMechId
    // check-errors

    Map createPartyContactMechResult = run service: "createPartyContactMech", with: [*: parameters,
                                                                                     contactMechId: newPartyContactMech.contactMechId,
                                                                                     contactMechTypeId: "POSTAL_ADDRESS"]
    if (!ServiceUtil.isSuccess(createPartyContactMechResult)) return createPartyContactMechResult

    result.contactMechId = newPartyContactMech.contactMechId
    result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyPostalAddressSuccessfullyCreated", locale)
    return result
}

Map updatePartyPostalAddress() {
    Map result = success()

    GenericValue newPartyContactMech = makeValue("PartyContactMech")
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }
    if (parameters.latitude) {
        if (parameters.longitude) {
            Map createGeoPointResult = run service: "createGeoPoint", with: [*: parameters]
            if (!ServiceUtil.isSuccess(createGeoPointResult)) return createGeoPointResult
            parameters.geoPointId = createGeoPointResult.geoPointId
            // check-errors
        }
    }
    Map updatePostalAddressResult = run service: "updatePostalAddress", with: [*: parameters]
    if (!ServiceUtil.isSuccess(updatePostalAddressResult)) return updatePostalAddressResult
    newPartyContactMech.contactMechId = updatePostalAddressResult.contactMechId

    logInfo("Copied id to updatePartyContactMechMap: ${newPartyContactMech.contactMechId}")
    Map updatePartyContactMechResult = run service: "updatePartyContactMech", with: [*: parameters,
                                                                                     newContactMechId: newPartyContactMech.contactMechId,
                                                                                     contactMechTypeId: "POSTAL_ADDRESS"]
    if (!ServiceUtil.isSuccess(updatePartyContactMechResult)) return updatePartyContactMechResult

    result.contactMechId = newPartyContactMech.contactMechId
    result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyPostalAddressSuccessfullyUpdated", locale)
    return result
}

Map createPartyTelecomNumber() {
    Map result = success()
    Map newPartyContactMech = [:]

    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }

    logInfo("Creating telecom number")
    Map createTelecomNumberResult = run service: "createTelecomNumber", with: [*: parameters]
    if (!ServiceUtil.isSuccess(createTelecomNumberResult)) return createTelecomNumberResult
    newPartyContactMech.contactMechId = createTelecomNumberResult.contactMechId
    logInfo("Copied id to createPartyContactMechMap: ${newPartyContactMech.contactMechId}")

    Map createPartyContactMechResult = run service: "createPartyContactMech", with: [*: parameters,
                                                                                     contactMechId: newPartyContactMech.contactMechId,
                                                                                     contactMechTypeId: "TELECOM_NUMBER"]
    if (!ServiceUtil.isSuccess(createPartyContactMechResult)) return createPartyContactMechResult

    result.contactMechId = newPartyContactMech.contactMechId
    result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyTelecomNumberSuccessfullyCreated", locale)
    return result
}

Map updatePartyTelecomNumber() {
    Map result = success()

    GenericValue newPartyContactMech = makeValue("PartyContactMech")
    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }

    Map updateTelecomNumberResult = run service: "updateTelecomNumber", with: [*: parameters]
    if (!ServiceUtil.isSuccess(updateTelecomNumberResult)) return updateTelecomNumberResult
    newPartyContactMech.contactMechId = updateTelecomNumberResult.contactMechId
    logInfo("Copied id to updatePartyContactMechMap: ${newPartyContactMech.contactMechId}")

    Map updatePartyContactMechResult = run service: "updatePartyContactMech", with: [*: parameters,
                                                                                     newContactMechId: newPartyContactMech.contactMechId,
                                                                                     contactMechTypeId: "TELECOM_NUMBER"
    ]
    if (!ServiceUtil.isSuccess(updatePartyContactMechResult)) return updatePartyContactMechResult
    logInfo("Setting result id: ${newPartyContactMech.contactMechId}")

    result.contactMechId = newPartyContactMech.contactMechId
    result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyTelecomNumberSuccessfullyUpdated", locale)
    return result
}

Map createPartyEmailAddress() {
    Map result = success()

    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }

    // if-validate-method isEmail : le <else> devient le cas "non valide"
    if (!UtilValidate.isEmail(parameters.emailAddress)) {
        return error(UtilProperties.getMessage("PartyUiLabels", "PartyEmailAddressNotFormattedCorrectly", locale))
    }

    // if e-mail address already exists simply return
    EntityCondition condition = EntityCondition.makeCondition([
            EntityCondition.makeCondition("partyId", parameters.partyId),
            EntityCondition.makeCondition("contactMechTypeId", "EMAIL_ADDRESS"),
            EntityCondition.makeCondition(EntityFunction.UPPER_FIELD("infoString"), EntityOperator.EQUALS, EntityFunction.UPPER(parameters.emailAddress))
    ], EntityOperator.AND)
    List partyAndContactMechs = from("PartyAndContactMech").where(condition).queryList()
    partyAndContactMechs = EntityUtil.filterByDate(partyAndContactMechs)
    if (partyAndContactMechs) {
        logInfo("E-mail address: ${parameters.emailAddress} already exists, did not add again..")
        GenericValue existsPartyAndContactMech = EntityUtil.getFirst(partyAndContactMechs)
        result.contactMechId = existsPartyAndContactMech.contactMechId
        return result
    }

    Map createPartyContactMechResult = run service: "createPartyContactMech", with: [*: parameters,
                                                                                     infoString: parameters.emailAddress,
                                                                                     contactMechTypeId: "EMAIL_ADDRESS"]
    if (!ServiceUtil.isSuccess(createPartyContactMechResult)) return createPartyContactMechResult
    result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyEmailAddressSuccessfullyCreated", locale)
    result.contactMechId = createPartyContactMechResult.contactMechId
    return result
}

Map updatePartyEmailAddress() {
    Map result = success()

    if (!parameters.partyId) {
        parameters.partyId = userLogin.partyId
    }

    if (!UtilValidate.isEmail(parameters.emailAddress)) {
        return error(UtilProperties.getMessage("PartyUiLabels", "PartyEmailAddressNotFormattedCorrectly", locale))
    }

    Map updatePartyContactMechResult = run service: "updatePartyContactMech", with: [*: parameters,
                                                                                     infoString: parameters.emailAddress,
                                                                                     contactMechTypeId: "EMAIL_ADDRESS"]
    if (!ServiceUtil.isSuccess(updatePartyContactMechResult)) return updatePartyContactMechResult
    result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyEmailAddressSuccessfullyUpdated", locale)
    result.contactMechId = updatePartyContactMechResult.contactMechId
    result.oldContactMechId = parameters.contactMechId
    return result
}

Map findPartyFromEmailAddress() {
    Map result = success()

    Map input = [:]
    input.inputFields = [:]
    input.filterByDate = "Y"
    input.inputFields.infoString = parameters.address
    String caseInsensitive = parameters.caseInsensitive
    if (!caseInsensitive) {
        caseInsensitive = EntityUtilProperties.getPropertyValue("general", "mail.address.caseInsensitive", "N", delegator)
    }
    input.inputFields.infoString_ic = caseInsensitive
    if (!parameters.fromDate) {
        input.filterByDate = "Y"
    } else {
        input.filterByDateValue = parameters.fromDate
    }
    // try primary email address
    input.inputFields.contactMechPurposeTypeId = "PRIMARY_EMAIL"
    input.entityName = "PartyContactDetailByPurpose"
    Map results = run service: "performFindItem", with: input
    if (!ServiceUtil.isSuccess(results)) return results
    // any other email address
    if (!results.item) {
        input.entityName = "PartyAndContactMech"
        input.inputFields.remove("contactMechPurposeTypeId") // clear-field
        results = run service: "performFindItem", with: input
        if (!ServiceUtil.isSuccess(results)) return results
    }
    if (results.item) {
        result.partyId = results.item.partyId
        result.contactMechId = results.item.contactMechId
    }
    return result
}

Map findPartyFromTelephone() {
    Map result = success()

    List contactMechs = from("PartyAndContactMech")
            .where("contactMechTypeId", "TELECOM_NUMBER")
            .filterByDate()
            .queryList()

    String dash = "-"
    String emptyString = ""
    String inputTelno = parameters.telno.replace(dash, emptyString)
    String partyId = null
    GenericValue contactMech = null // déclaré hors boucle : minilang conserve la dernière valeur itérée
    for (contactMech in contactMechs) {
        String telno = (contactMech.tnContactNumber ?: "").replace(dash, emptyString)
        if (inputTelno == telno) {
            partyId = contactMech.partyId
        }
        telno = "${contactMech.tnAreaCode ?: ""}${telno}"
        if (inputTelno == telno) {
            partyId = contactMech.partyId
        }
        telno = "${contactMech.tnCountryCode ?: ""}${telno}"
        if (inputTelno == telno) {
            partyId = contactMech.partyId
        }
        telno = "+${telno}"
        if (inputTelno == telno) {
            partyId = contactMech.partyId
        }
    }
    if (partyId) {
        result.partyId = partyId
        result.contactMechId = contactMech.contactMechId
    }
    return result
}

Map findPartyFromTelephoneComplete() {
    Map result = success()

    List contactMechs = from("PartyAndContactMech")
            .where("contactMechTypeId", "TELECOM_NUMBER")
            .filterByDate()
            .queryList()

    String dash = "-"
    String emptyString = ""
    String inputTelno = parameters.telno
    String partyId = null
    GenericValue contactMech = null // idem : dernière valeur itérée conservée
    for (contactMech in contactMechs) {
        String telno = contactMech.tnContactNumber
        // telno = contactMech.tnContactNumber.replace(dash, emptyString)   (commenté dans l"original)
        if (inputTelno == telno) {
            partyId = contactMech.partyId
        }
        telno = "${contactMech.tnAreaCode ?: ""}${telno ?: ""}"
        if (inputTelno == telno) {
            partyId = contactMech.partyId
        }
        telno = "${contactMech.tnCountryCode ?: ""}${telno}"
        if (inputTelno == telno) {
            partyId = contactMech.partyId
        }
        telno = "+${telno}"
        if (inputTelno == telno) {
            partyId = contactMech.partyId
        }
    }
    if (partyId) {
        result.partyId = partyId
        result.contactMechId = contactMech.contactMechId
    }
    return result
}

// login-required="false" => auth="false" dans la définition du service
Map createPostalAddressAndPurposes() {
    Map result = success()

    if (parameters.roleTypeId) {
        // entity-one sans field-map : les clés primaires viennent du contexte
        GenericValue partyRole = from("PartyRole")
                .where("partyId", parameters.partyId, "roleTypeId", parameters.roleTypeId)
                .queryOne()
        if (!partyRole) {
            String roleTypeId = parameters.roleTypeId
            return error(UtilProperties.getMessage("PartyUiLabels", "PartyRoleTypeNotFoundForTheParty", locale))
        }
    }
    Map createPartyPostalAddressResult = run service: "createPartyPostalAddress", with: parameters
    if (!ServiceUtil.isSuccess(createPartyPostalAddressResult)) return createPartyPostalAddressResult
    parameters.contactMechId = createPartyPostalAddressResult.contactMechId
    result.contactMechId = createPartyPostalAddressResult.contactMechId

    if (parameters.setShippingPurpose || parameters.setBillingPurpose) {
        if ("Y".equals(parameters.setShippingPurpose)) {
            List pcmpList = from("PartyContactMechPurpose")
                    .where("partyId", userLogin.partyId, "contactMechPurposeTypeId", "SHIPPING_LOCATION")
                    .filterByDate()
                    .queryList()
            for (GenericValue pcmp : pcmpList) {
                Map expireResult = run service: "expirePartyContactMechPurpose", with: pcmp
                if (!ServiceUtil.isSuccess(expireResult)) return expireResult
            }
            Map createPurposeResult = run service: "createPartyContactMechPurpose", with: [*: parameters,
                                                                                           partyId: userLogin.partyId,
                                                                                           contactMechPurposeTypeId: "SHIPPING_LOCATION"]

            if (!ServiceUtil.isSuccess(createPurposeResult)) return createPurposeResult

            Map profileResult = run service: "setPartyProfileDefaults", with: [*: parameters,
                                                                               defaultShipAddr: parameters.contactMechId,
                                                                               partyId: userLogin.partyId]
            if (!ServiceUtil.isSuccess(profileResult)) return profileResult
        }
        if ("Y".equals(parameters.setBillingPurpose)) {
            List pcmpList = from("PartyContactMechPurpose")
                    .where("partyId", userLogin.partyId, "contactMechPurposeTypeId", "BILLING_LOCATION")
                    .filterByDate()
                    .queryList()
            for (GenericValue pcmp : pcmpList) {
                Map expireResult = run service: "expirePartyContactMechPurpose", with: pcmp
                if (!ServiceUtil.isSuccess(expireResult)) return expireResult
            }
            Map createPurposeResult = run service: "createPartyContactMechPurpose", with: [*: parameters,
                                                                                           partyId: userLogin.partyId,
                                                                                           contactMechPurposeTypeId: "BILLING_LOCATION"]
            if (!ServiceUtil.isSuccess(createPurposeResult)) return createPurposeResult

            Map profileResult = run service: "setPartyProfileDefaults", with: [*: parameters,
                                                                               defaultBillAddr: parameters.contactMechId,
                                                                               partyId: userLogin.partyId]
            if (!ServiceUtil.isSuccess(profileResult)) return profileResult
        }
    }
    return result
}

// login-required="false" => auth="false" dans la définition du service
Map updatePostalAddressAndPurposes() {
    Map result = success()

    GenericValue partyProfileDefault = from("PartyProfileDefault")
            .where("partyId", userLogin.partyId, "productStoreId", parameters.productStoreId)
            .queryOne()
    if (parameters.contactMechId == partyProfileDefault?.defaultBillAddr
            || parameters.contactMechId == partyProfileDefault?.defaultShipAddr) {
        if (partyProfileDefault.defaultBillAddr != partyProfileDefault.defaultShipAddr) {
            Map updatePartyPostalAddressResult = run service: "updatePartyPostalAddress", with: parameters
            if (!ServiceUtil.isSuccess(updatePartyPostalAddressResult)) return updatePartyPostalAddressResult
            parameters.contactMechId = updatePartyPostalAddressResult.contactMechId
            result.contactMechId = updatePartyPostalAddressResult.contactMechId
        } else {
            Map updatePostalAddressResult = run service: "updatePostalAddress", with: [*: parameters]
            if (!ServiceUtil.isSuccess(updatePostalAddressResult)) return updatePostalAddressResult
            result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyPostalAddressSuccessfullyUpdated", locale)
            parameters.newContactMechId = updatePostalAddressResult.contactMechId
            result.contactMechId = updatePostalAddressResult.contactMechId

            if (parameters.contactMechId != parameters.newContactMechId) {
                Map createPartyContactMechResult = run service: "createPartyContactMech", with: [*: parameters,
                                                                                                 contactMechId: parameters.newContactMechId,
                                                                                                 contactMechTypeId: "POSTAL_ADDRESS"]
                if (!ServiceUtil.isSuccess(createPartyContactMechResult)) return createPartyContactMechResult
                result.successMessage = UtilProperties.getMessage("PartyUiLabels", "PartyPostalAddressSuccessfullyCreated", locale)
            }
            parameters.contactMechId = parameters.newContactMechId
        }
    } else {
        Map updatePartyPostalAddressResult = run service: "updatePartyPostalAddress", with: parameters
        if (!ServiceUtil.isSuccess(updatePartyPostalAddressResult)) return updatePartyPostalAddressResult
        parameters.contactMechId = updatePartyPostalAddressResult.contactMechId
        result.contactMechId = updatePartyPostalAddressResult.contactMechId
    }

    // Setting the purposes
    if (parameters.setShippingPurpose || parameters.setBillingPurpose) {
        if ("Y".equals(parameters.setShippingPurpose)) {
            List pcmpShipList = from("PartyContactMechPurpose")
                    .where("partyId", userLogin.partyId,
                            "contactMechId", parameters.contactMechId,
                            "contactMechPurposeTypeId", "SHIPPING_LOCATION")
                    .filterByDate()
                    .queryList()
            // If purpose is not exists then create
            if (!pcmpShipList) {

                List pcmpList = from("PartyContactMechPurpose")
                        .where("partyId", userLogin.partyId, "contactMechPurposeTypeId", "SHIPPING_LOCATION")
                        .filterByDate()
                        .queryList()
                for (GenericValue pcmp : pcmpList) {
                    Map expireResult = run service: "expirePartyContactMechPurpose", with: pcmp
                    if (!ServiceUtil.isSuccess(expireResult)) return expireResult
                }
                Map createPurposeResult = run service: "createPartyContactMechPurpose", with:
                        [*: parameters,
                         partyId: userLogin.partyId,
                         contactMechPurposeTypeId: "SHIPPING_LOCATION"]
                if (!ServiceUtil.isSuccess(createPurposeResult)) return createPurposeResult
            }

            Map profileResult = run service: "setPartyProfileDefaults", with: [*: parameters,
                                                                               defaultShipAddr: parameters.contactMechId,
                                                                               partyId: userLogin.partyId]
            if (!ServiceUtil.isSuccess(profileResult)) return profileResult
        }
        if ("Y".equals(parameters.setBillingPurpose)) {
            List pcmpBillList = from("PartyContactMechPurpose")
                    .where("partyId", userLogin.partyId,
                            "contactMechId", parameters.contactMechId,
                            "contactMechPurposeTypeId", "BILLING_LOCATION")
                    .filterByDate()
                    .queryList()
            // If purpose is not exists then create
            if (!pcmpBillList) {
                List pcmpList = from("PartyContactMechPurpose")
                        .where("partyId", userLogin.partyId, "contactMechPurposeTypeId", "BILLING_LOCATION")
                        .filterByDate()
                        .queryList()
                for (GenericValue pcmp : pcmpList) {
                    Map expireResult = run service: "expirePartyContactMechPurpose", with: pcmp
                    if (!ServiceUtil.isSuccess(expireResult)) return expireResult
                }
                Map createPurposeResult = run service: "createPartyContactMechPurpose", with:
                        [*: parameters,
                         partyId: userLogin.partyId,
                         contactMechPurposeTypeId: "BILLING_LOCATION"]
                if (!ServiceUtil.isSuccess(createPurposeResult)) return createPurposeResult
            }

            Map profileResult = run service: "setPartyProfileDefaults", with: [*: parameters,
                                                                               defaultBillAddr: parameters.contactMechId,
                                                                               partyId: userLogin.partyId]
            if (!ServiceUtil.isSuccess(profileResult)) return profileResult
        }
    }
    return result
}

Map updateContactMechAndPurposes() {
    Map result = success()
    Map updatePostalAddressAndPurposesResult = run service: "updatePostalAddressAndPurposes", with: [*: parameters]
    if (!ServiceUtil.isSuccess(updatePostalAddressAndPurposesResult)) return updatePostalAddressAndPurposesResult
    result.contactMechId = updatePostalAddressAndPurposesResult.contactMechId

    if (parameters.phoneContactMechId) {
        Map updatePartyTelecomNumberResult = run service: "updatePartyTelecomNumber", with: [*: parameters,
                                                                                             contactMechId: parameters.phoneContactMechId]
        if (!ServiceUtil.isSuccess(updatePartyTelecomNumberResult)) return updatePartyTelecomNumberResult
    }
    return result
}

// login-required="false" => auth="false" dans la définition du service
Map createUpdatePartyEmailAddress() {
    Map result = success()

    String contactMechId = null
    if (!parameters.contactMechId) {
        Map createResult = run service: "createPartyEmailAddress", with: [*: parameters,
                                                                          partyId: parameters.partyId ?: userLogin.partyId]
        if (!ServiceUtil.isSuccess(createResult)) return createResult
        contactMechId = createResult.contactMechId
        logInfo("Email Contact Created emailContactMechId is ${contactMechId}")
    } else {
        Map updateResult = run service: "updatePartyEmailAddress", with: [*: parameters]
        if (!ServiceUtil.isSuccess(updateResult)) return updateResult
        contactMechId = updateResult.contactMechId
        logInfo("Email Contact updated emailContactMechId is ${contactMechId}")
    }
    // entity-one sans field-map : la clé vient de la variable contactMechId du contexte
    GenericValue contactMech = from("ContactMech").where("contactMechId", contactMechId).queryOne()
    result.emailAddress = contactMech.infoString
    result.contactMechId = contactMechId
    return result
}

// login-required="false" => auth="false" dans la définition du service
Map createUpdatePartyTelecomNumber() {
    Map result = success()

    String contactMechId = null
    if (!parameters.contactMechId) {
        Map createResult = run service: "createPartyTelecomNumber", with: [*: parameters]
        if (!ServiceUtil.isSuccess(createResult)) return createResult
        contactMechId = createResult.contactMechId
        logInfo("Phone Contact created phoneContactMechId is ${contactMechId}")
    } else {
        Map updateResult = run service: "updatePartyTelecomNumber", with: [*: parameters]
        if (!ServiceUtil.isSuccess(updateResult)) return updateResult
        contactMechId = updateResult.contactMechId
        logInfo("Phone Contact updated phoneContactMechId is ${contactMechId}")
    }
    result.contactMechId = contactMechId
    return result
}

// login-required="false" => auth="false" dans la définition du service
Map createUpdatePartyPostalAddress() {
    Map result = success()

    String contactMechId
    if (!parameters.contactMechId) {
        Map createResult = run service: "createPartyPostalAddress", with: [*: parameters]
        if (!ServiceUtil.isSuccess(createResult)) return createResult
        contactMechId = createResult.contactMechId
        logInfo("Postal address created, contactMechId is ${contactMechId}")
    } else {
        Map updateResult = run service: "updatePartyPostalAddress", with: [*: parameters]
        if (!ServiceUtil.isSuccess(updateResult)) return updateResult
        contactMechId = updateResult.contactMechId
        logInfo("Postal address updated, contactMechId is ${contactMechId}")
    }
    result.contactMechId = contactMechId
    return result
}
