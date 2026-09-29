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
package org.apache.ofbiz.party.contact

import static org.apache.ofbiz.service.ServiceUtil.isSuccess

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityFunction
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.entity.util.EntityUtilProperties

Map createPartyContactMech() {
    parameters.partyId = parameters.partyId ?: userLogin.partyId

    // check if the contact mech infostring is already existing if so, do not create a new one
    List partyAndContactMechs = from('PartyAndContactMech')
            .where('partyId', parameters.partyId)
            .filterByDate()
            .queryList()
    GenericValue alreadyExistingCMech = partyAndContactMechs.find { partyAndContactMech ->
        GenericValue contactMechType = from('ContactMechType')
                .where('contactMechTypeId', partyAndContactMech.contactMechTypeId)
                .cache()
                .queryOne()
        return (contactMechType.hasTable == 'N'
                && parameters.infoString == partyAndContactMech.infoString
                && parameters.contactMechTypeId == partyAndContactMech.contactMechTypeId)
    }
    if (alreadyExistingCMech) {
        logInfo("ContactMechId: ${alreadyExistingCMech.contactMechId}" +
                " already exists with value: ${alreadyExistingCMech.infoString} for party: ${parameters.partyId}" +
                " and ContactMechTypeId: ${alreadyExistingCMech.contactMechTypeId}")
        return success(contactMechId: alreadyExistingCMech.contactMechId)
    }
    GenericValue newValue = makeValue('PartyContactMech')
    newValue.setNonPKFields(parameters)
    newValue.partyId = parameters.partyId
    newValue.fromDate = UtilDateTime.nowTimestamp()
    if (parameters.contactMechId) {
        newValue.contactMechId = parameters.contactMechId
        logInfo("Creating a PartyContactMech with id: ${parameters.contactMechId}")
    } else {
        Map createContactMechResult = run service: 'createContactMech', with: [*: parameters]
        if (!isSuccess(createContactMechResult)) {
            return createContactMechResult
        }
        newValue.contactMechId = createContactMechResult.contactMechId
        logInfo('ContactMech created')
        logInfo("Creating a PartyContactMech with id: ${newValue.contactMechId}")
    }
    newValue.create()
    return success(contactMechId: newValue.contactMechId)
}

Map updatePartyContactMech() {
    Map result = success()
    parameters.partyId = parameters.partyId ?: userLogin.partyId

    GenericValue partyContactMechMap = makeValue('PartyContactMech')
    partyContactMechMap.setPKFields(parameters)
    List partyContactMechs = from('PartyContactMech').where(partyContactMechMap).queryList()
    List validPartyContactMechs = EntityUtil.filterByDate(partyContactMechs)
    GenericValue partyContactMech = EntityUtil.getFirst(validPartyContactMechs)
    if (!partyContactMech) {
        return error(label('PartyUiLabels', 'PartyCannotUpdateContactBecauseNotWithSpecifiedParty'))
    }

    GenericValue newPartyContactMech = (GenericValue) partyContactMech.clone()

    // If we already have a new contactMechId don't update ContactMech
    if (parameters.newContactMechId) {
        newPartyContactMech.contactMechId = parameters.newContactMechId
        logInfo("Using supplied new contact mech id: ${newPartyContactMech.contactMechId}")
    } else {
        Map updateContactMechResult = run service: 'updateContactMech', with: [*: parameters]
        if (!isSuccess(updateContactMechResult)) {
            return updateContactMechResult
        }
        // default-message PartyContactMechanismSuccessfullyUpdated
        result.successMessage = label('PartyUiLabels', 'PartyContactMechanismSuccessfullyUpdated')
        newPartyContactMech.contactMechId = updateContactMechResult.contactMechId
    }

    if (newPartyContactMech.contactMechId != parameters.contactMechId) {
        newPartyContactMech.setNonPKFields(parameters)
        newPartyContactMech.fromDate = UtilDateTime.nowTimestamp()
        partyContactMech.thruDate = UtilDateTime.nowTimestamp()
        partyContactMech.store()
        newPartyContactMech.create()
        List partyContactMechPurposes = partyContactMech.getRelated('PartyContactMechPurpose', null, null, false)
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
            List purposeResult = from('PartyContactMechPurpose').where(purposeMap).queryList()

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
            partyContactMech.thruDate = null // value='' en minilang => null
        }
        partyContactMech.store()
        logInfo("Setting id to result: ${partyContactMech.contactMechId}")
        result.contactMechId = partyContactMech.contactMechId
    }
    return result
}

Map deletePartyContactMech() {
    parameters.partyId = parameters.partyId ?: userLogin.partyId

    GenericValue partyContactMechMap = makeValue('PartyContactMech')
    partyContactMechMap.setPKFields(parameters)
    List partyContactMechs = from('PartyContactMech').where(partyContactMechMap).queryList()
    List validPartyContactMechs = EntityUtil.filterByDate(partyContactMechs)
    GenericValue partyContactMech = EntityUtil.getFirst(validPartyContactMechs)
    if (!partyContactMech) {
        return error(label('PartyUiLabels', 'PartyContactMechNotFoundCannotDelete'))
    }
    partyContactMech.thruDate = UtilDateTime.nowTimestamp()
    partyContactMech.store()
    return success()
}

Map createPartyPostalAddress() {
    Map result = success()
    Map newPartyContactMech = [:]
    parameters.partyId = parameters.partyId ?: userLogin.partyId
    if (parameters.latitude) {
        if (parameters.longitude) {
            Map createGeoPointResult = run service: 'createGeoPoint', with: [*: parameters]
            if (!isSuccess(createGeoPointResult)) {
                return createGeoPointResult
            }
            parameters.geoPointId = createGeoPointResult.geoPointId
            // check-errors
        }
    }
    Map createPostalAddressResult = run service: 'createPostalAddress', with: [*: parameters]
    if (!isSuccess(createPostalAddressResult)) {
        return createPostalAddressResult
    }
    newPartyContactMech.contactMechId = createPostalAddressResult.contactMechId
    // check-errors

    Map createPartyContactMechResult = run service: 'createPartyContactMech', with: [*: parameters,
                                                                                     contactMechId: newPartyContactMech.contactMechId,
                                                                                     contactMechTypeId: 'POSTAL_ADDRESS']
    if (!isSuccess(createPartyContactMechResult)) {
        return createPartyContactMechResult
    }

    result.contactMechId = newPartyContactMech.contactMechId
    result.successMessage = label('PartyUiLabels', 'PartyPostalAddressSuccessfullyCreated')
    return result
}

Map updatePartyPostalAddress() {
    Map result = success()
    GenericValue newPartyContactMech = makeValue('PartyContactMech')
    parameters.partyId = parameters.partyId ?: userLogin.partyId
    if (parameters.latitude) {
        if (parameters.longitude) {
            Map createGeoPointResult = run service: 'createGeoPoint', with: [*: parameters]
            if (!isSuccess(createGeoPointResult)) {
                return createGeoPointResult
            }
            parameters.geoPointId = createGeoPointResult.geoPointId
            // check-errors
        }
    }
    Map updatePostalAddressResult = run service: 'updatePostalAddress', with: [*: parameters]
    if (!isSuccess(updatePostalAddressResult)) {
        return updatePostalAddressResult
    }
    newPartyContactMech.contactMechId = updatePostalAddressResult.contactMechId

    logInfo("Copied id to updatePartyContactMechMap: ${newPartyContactMech.contactMechId}")
    Map updatePartyContactMechResult = run service: 'updatePartyContactMech', with: [*: parameters,
                                                                                     newContactMechId: newPartyContactMech.contactMechId,
                                                                                     contactMechTypeId: 'POSTAL_ADDRESS']
    if (!isSuccess(updatePartyContactMechResult)) {
        return updatePartyContactMechResult
    }

    result.contactMechId = newPartyContactMech.contactMechId
    result.successMessage = label('PartyUiLabels', 'PartyPostalAddressSuccessfullyUpdated')
    return result
}

Map createPartyTelecomNumber() {
    Map result = success()
    Map newPartyContactMech = [:]
    parameters.partyId = parameters.partyId ?: userLogin.partyId

    logInfo('Creating telecom number')
    Map createTelecomNumberResult = run service: 'createTelecomNumber', with: [*: parameters]
    if (!isSuccess(createTelecomNumberResult)) {
        return createTelecomNumberResult
    }
    newPartyContactMech.contactMechId = createTelecomNumberResult.contactMechId
    logInfo("Copied id to createPartyContactMechMap: ${newPartyContactMech.contactMechId}")

    Map createPartyContactMechResult = run service: 'createPartyContactMech', with: [*: parameters,
                                                                                     contactMechId: newPartyContactMech.contactMechId,
                                                                                     contactMechTypeId: 'TELECOM_NUMBER']
    if (!isSuccess(createPartyContactMechResult)) {
        return createPartyContactMechResult
    }

    result.contactMechId = newPartyContactMech.contactMechId
    result.successMessage = label('PartyUiLabels', 'PartyTelecomNumberSuccessfullyCreated')
    return result
}

Map updatePartyTelecomNumber() {
    Map result = success()
    GenericValue newPartyContactMech = makeValue('PartyContactMech')
    parameters.partyId = parameters.partyId ?: userLogin.partyId

    Map updateTelecomNumberResult = run service: 'updateTelecomNumber', with: [*: parameters]
    if (!isSuccess(updateTelecomNumberResult)) {
        return updateTelecomNumberResult
    }
    newPartyContactMech.contactMechId = updateTelecomNumberResult.contactMechId
    logInfo("Copied id to updatePartyContactMechMap: ${newPartyContactMech.contactMechId}")

    Map updatePartyContactMechResult = run service: 'updatePartyContactMech', with: [*: parameters,
                                                                                     newContactMechId: newPartyContactMech.contactMechId,
                                                                                     contactMechTypeId: 'TELECOM_NUMBER'
    ]
    if (!isSuccess(updatePartyContactMechResult)) {
        return updatePartyContactMechResult
    }
    logInfo("Setting result id: ${newPartyContactMech.contactMechId}")

    result.contactMechId = newPartyContactMech.contactMechId
    result.successMessage = label('PartyUiLabels', 'PartyTelecomNumberSuccessfullyUpdated')
    return result
}

Map createPartyEmailAddress() {
    Map result = success()
    parameters.partyId = parameters.partyId ?: userLogin.partyId
    if (!UtilValidate.isEmail(parameters.emailAddress as String)) {
        return error(label('PartyUiLabels', 'PartyEmailAddressNotFormattedCorrectly'))
    }

    EntityCondition condition = EntityCondition.makeCondition([
            EntityCondition.makeCondition('partyId', parameters.partyId),
            EntityCondition.makeCondition('contactMechTypeId', 'EMAIL_ADDRESS'),
            EntityCondition.makeCondition(EntityFunction.upper('infoString'),
                    EntityOperator.EQUALS,
                    EntityFunction.upper(parameters.emailAddress))
    ], EntityOperator.AND)
    List partyAndContactMechs = from('PartyAndContactMech').where(condition).queryList()
    partyAndContactMechs = EntityUtil.filterByDate(partyAndContactMechs)
    if (partyAndContactMechs) {
        logInfo("E-mail address: ${parameters.emailAddress} already exists, did not add again..")
        GenericValue existsPartyAndContactMech = EntityUtil.getFirst(partyAndContactMechs)
        result.contactMechId = existsPartyAndContactMech.contactMechId
        return result
    }

    Map createPartyContactMechResult = run service: 'createPartyContactMech', with: [*: parameters,
                                                                                     infoString: parameters.emailAddress,
                                                                                     contactMechTypeId: 'EMAIL_ADDRESS']
    if (!isSuccess(createPartyContactMechResult)) {
        return createPartyContactMechResult
    }
    result.successMessage = label('PartyUiLabels', 'PartyEmailAddressSuccessfullyCreated')
    result.contactMechId = createPartyContactMechResult.contactMechId
    return result
}

Map updatePartyEmailAddress() {
    Map result = success()
    parameters.partyId = parameters.partyId ?: userLogin.partyId

    if (!UtilValidate.isEmail(parameters.emailAddress)) {
        return error(label('PartyUiLabels', 'PartyEmailAddressNotFormattedCorrectly'))
    }

    Map updatePartyContactMechResult = run service: 'updatePartyContactMech', with: [*: parameters,
                                                                                     infoString: parameters.emailAddress,
                                                                                     contactMechTypeId: 'EMAIL_ADDRESS']
    if (!isSuccess(updatePartyContactMechResult)) {
        return updatePartyContactMechResult
    }
    result.successMessage = label('PartyUiLabels', 'PartyEmailAddressSuccessfullyUpdated')
    result.contactMechId = updatePartyContactMechResult.contactMechId
    result.oldContactMechId = parameters.contactMechId
    return result
}

Map findPartyFromEmailAddress() {
    Map result = success()

    Map input = [:]
    input.inputFields = [:]
    input.filterByDate = 'Y'
    input.inputFields.infoString = parameters.address
    String caseInsensitive = parameters.caseInsensitive ?:
            EntityUtilProperties.getPropertyValue('general', 'mail.address.caseInsensitive', 'N', delegator)

    input.inputFields.infoString_ic = caseInsensitive
    if (parameters.fromDate) {
        input.filterByDateValue = parameters.fromDate
    } else {
        input.filterByDate = 'Y'
    }
    // try primary email address
    input.inputFields.contactMechPurposeTypeId = 'PRIMARY_EMAIL'
    input.entityName = 'PartyContactDetailByPurpose'
    Map results = run service: 'performFindItem', with: input
    if (!isSuccess(results)) {
        return results
    }
    // any other email address
    if (!results.item) {
        input.entityName = 'PartyAndContactMech'
        input.inputFields.remove('contactMechPurposeTypeId') // clear-field
        results = run service: 'performFindItem', with: input
        if (!isSuccess(results)) {
            return results
        }
    }
    if (results.item) {
        result.partyId = results.item.partyId
        result.contactMechId = results.item.contactMechId
    }
    return result
}

Map findPartyFromTelephone() {
    Map result = success()
    List contactMechs = from('PartyAndContactMech')
            .where('contactMechTypeId', 'TELECOM_NUMBER')
            .filterByDate()
            .queryList()
    boolean complete = parameters.complete == 'Y'
    String inputTelno = complete ? parameters.telno : parameters.telno.replace('-', '')
    GenericValue relevantContactMech = contactMechs.find { contactMech ->
        String phoneNb = complete ? (contactMech.tnContactNumber ?: '') : (contactMech.tnContactNumber ?: '').replace('-', '')
        String tnAreaCode = contactMech.tnAreaCode ?: ''
        return inputTelno == phoneNb ||
                inputTelno == "$tnAreaCode$phoneNb" ||
                inputTelno == "${contactMech.tnCountryCode ?: ''}$tnAreaCode$phoneNb" ||
                inputTelno == "+${contactMech.tnCountryCode ?: ''}$tnAreaCode$phoneNb"
    }
    if (relevantContactMech) {
        result.partyId = relevantContactMech.partyId
        result.contactMechId = relevantContactMech.contactMechId
    }
    return result
}

Map createPostalAddressAndPurposes() {
    Map result = success()

    if (parameters.roleTypeId) {
        // entity-one sans field-map : les clés primaires viennent du contexte
        GenericValue partyRole = from('PartyRole')
                .where('partyId', parameters.partyId, 'roleTypeId', parameters.roleTypeId)
                .queryOne()
        if (!partyRole) {
            String roleTypeId = parameters.roleTypeId
            return error(label('PartyUiLabels', 'PartyRoleTypeNotFoundForTheParty', [roleTypeId: roleTypeId]))
        }
    }
    Map createPartyPostalAddressResult = run service: 'createPartyPostalAddress', with: parameters
    if (!isSuccess(createPartyPostalAddressResult)) {
        return createPartyPostalAddressResult
    }
    parameters.contactMechId = createPartyPostalAddressResult.contactMechId
    result.contactMechId = createPartyPostalAddressResult.contactMechId

    if (parameters.setShippingPurpose || parameters.setBillingPurpose) {
        if (parameters.setShippingPurpose == 'Y') {
            List pcmpList = from('PartyContactMechPurpose')
                    .where('partyId', userLogin.partyId, 'contactMechPurposeTypeId', 'SHIPPING_LOCATION')
                    .filterByDate()
                    .queryList()
            for (GenericValue pcmp : pcmpList) {
                Map expireResult = run service: 'expirePartyContactMechPurpose', with: pcmp
                if (!isSuccess(expireResult)) {
                    return expireResult
                }
            }
            Map createPurposeResult = run service: 'createPartyContactMechPurpose', with: [*: parameters,
                                                                                           partyId: userLogin.partyId,
                                                                                           contactMechPurposeTypeId: 'SHIPPING_LOCATION']

            if (!isSuccess(createPurposeResult)) {
                return createPurposeResult
            }

            Map profileResult = run service: 'setPartyProfileDefaults', with: [*: parameters,
                                                                               defaultShipAddr: parameters.contactMechId,
                                                                               partyId: userLogin.partyId]
            if (!isSuccess(profileResult)) {
                return profileResult
            }
        }
        if (parameters.setBillingPurpose == 'Y') {
            List pcmpList = from('PartyContactMechPurpose')
                    .where('partyId', userLogin.partyId, 'contactMechPurposeTypeId', 'BILLING_LOCATION')
                    .filterByDate()
                    .queryList()
            for (GenericValue pcmp : pcmpList) {
                Map expireResult = run service: 'expirePartyContactMechPurpose', with: pcmp
                if (!isSuccess(expireResult)) {
                    return expireResult
                }
            }
            Map createPurposeResult = run service: 'createPartyContactMechPurpose', with: [*: parameters,
                                                                                           partyId: userLogin.partyId,
                                                                                           contactMechPurposeTypeId: 'BILLING_LOCATION']
            if (!isSuccess(createPurposeResult)) {
                return createPurposeResult
            }

            Map profileResult = run service: 'setPartyProfileDefaults', with: [*: parameters,
                                                                               defaultBillAddr: parameters.contactMechId,
                                                                               partyId: userLogin.partyId]
            if (!isSuccess(profileResult)) {
                return profileResult
            }
        }
    }
    return result
}

Map updatePostalAddressAndPurposes() {
    Map result = success()

    GenericValue partyProfileDefault = from('PartyProfileDefault')
            .where('partyId', userLogin.partyId, 'productStoreId', parameters.productStoreId)
            .queryOne()
    if (parameters.contactMechId == partyProfileDefault?.defaultBillAddr
            || parameters.contactMechId == partyProfileDefault?.defaultShipAddr) {
        if (partyProfileDefault.defaultBillAddr != partyProfileDefault.defaultShipAddr) {
            Map updatePartyPostalAddressResult = run service: 'updatePartyPostalAddress', with: parameters
            if (!isSuccess(updatePartyPostalAddressResult)) {
                return updatePartyPostalAddressResult
            }
            parameters.contactMechId = updatePartyPostalAddressResult.contactMechId
            result.contactMechId = updatePartyPostalAddressResult.contactMechId
        } else {
            Map updatePostalAddressResult = run service: 'updatePostalAddress', with: [*: parameters]
            if (!isSuccess(updatePostalAddressResult)) {
                return updatePostalAddressResult
            }
            result.successMessage = label('PartyUiLabels', 'PartyPostalAddressSuccessfullyUpdated')
            parameters.newContactMechId = updatePostalAddressResult.contactMechId
            result.contactMechId = updatePostalAddressResult.contactMechId

            if (parameters.contactMechId != parameters.newContactMechId) {
                Map createPartyContactMechResult = run service: 'createPartyContactMech', with: [*: parameters,
                                                                                                 contactMechId: parameters.newContactMechId,
                                                                                                 contactMechTypeId: 'POSTAL_ADDRESS']
                if (!isSuccess(createPartyContactMechResult)) {
                    return createPartyContactMechResult
                }
                result.successMessage = label('PartyUiLabels', 'PartyPostalAddressSuccessfullyCreated')
            }
            parameters.contactMechId = parameters.newContactMechId
        }
    } else {
        Map updatePartyPostalAddressResult = run service: 'updatePartyPostalAddress', with: parameters
        if (!isSuccess(updatePartyPostalAddressResult)) {
            return updatePartyPostalAddressResult
        }
        parameters.contactMechId = updatePartyPostalAddressResult.contactMechId
        result.contactMechId = updatePartyPostalAddressResult.contactMechId
    }

    // Setting the purposes
    if (parameters.setShippingPurpose || parameters.setBillingPurpose) {
        if (parameters.setShippingPurpose == 'Y') {
            List pcmpShipList = from('PartyContactMechPurpose')
                    .where('partyId', userLogin.partyId,
                            'contactMechId', parameters.contactMechId,
                            'contactMechPurposeTypeId', 'SHIPPING_LOCATION')
                    .filterByDate()
                    .queryList()
            // If purpose is not exists then create
            if (!pcmpShipList) {
                List pcmpList = from('PartyContactMechPurpose')
                        .where('partyId', userLogin.partyId, 'contactMechPurposeTypeId', 'SHIPPING_LOCATION')
                        .filterByDate()
                        .queryList()
                for (GenericValue pcmp : pcmpList) {
                    Map expireResult = run service: 'expirePartyContactMechPurpose', with: pcmp
                    if (!isSuccess(expireResult)) {
                        return expireResult
                    }
                }
                Map createPurposeResult = run service: 'createPartyContactMechPurpose', with:
                        [*: parameters,
                         partyId: userLogin.partyId,
                         contactMechPurposeTypeId: 'SHIPPING_LOCATION']
                if (!isSuccess(createPurposeResult)) {
                    return createPurposeResult
                }
            }

            Map profileResult = run service: 'setPartyProfileDefaults', with: [*: parameters,
                                                                               defaultShipAddr: parameters.contactMechId,
                                                                               partyId: userLogin.partyId]
            if (!isSuccess(profileResult)) {
                return profileResult
            }
        }
        if (parameters.setBillingPurpose == 'Y') {
            List pcmpBillList = from('PartyContactMechPurpose')
                    .where('partyId', userLogin.partyId,
                            'contactMechId', parameters.contactMechId,
                            'contactMechPurposeTypeId', 'BILLING_LOCATION')
                    .filterByDate()
                    .queryList()
            // If purpose is not exists then create
            if (!pcmpBillList) {
                List pcmpList = from('PartyContactMechPurpose')
                        .where('partyId', userLogin.partyId, 'contactMechPurposeTypeId', 'BILLING_LOCATION')
                        .filterByDate()
                        .queryList()
                for (GenericValue pcmp : pcmpList) {
                    Map expireResult = run service: 'expirePartyContactMechPurpose', with: pcmp
                    if (!isSuccess(expireResult)) {
                        return expireResult
                    }
                }
                Map createPurposeResult = run service: 'createPartyContactMechPurpose', with:
                        [*: parameters,
                         partyId: userLogin.partyId,
                         contactMechPurposeTypeId: 'BILLING_LOCATION']
                if (!isSuccess(createPurposeResult)) {
                    return createPurposeResult
                }
            }

            Map profileResult = run service: 'setPartyProfileDefaults', with: [*: parameters,
                                                                               defaultBillAddr: parameters.contactMechId,
                                                                               partyId: userLogin.partyId]
            if (!isSuccess(profileResult)) {
                return profileResult
            }
        }
    }
    return result
}

Map updateContactMechAndPurposes() {
    Map result = success()
    Map updatePostalAddressAndPurposesResult = run service: 'updatePostalAddressAndPurposes', with: [*: parameters]
    if (!isSuccess(updatePostalAddressAndPurposesResult)) {
        return updatePostalAddressAndPurposesResult
    }
    result.contactMechId = updatePostalAddressAndPurposesResult.contactMechId

    if (parameters.phoneContactMechId) {
        Map updatePartyTelecomNumberResult = run service: 'updatePartyTelecomNumber', with: [*: parameters,
                                                                                             contactMechId: parameters.phoneContactMechId]
        if (!isSuccess(updatePartyTelecomNumberResult)) {
            return updatePartyTelecomNumberResult
        }
    }
    return result
}

Map createUpdatePartyEmailAddress() {
    Map result = success()

    String contactMechId = null
    if (parameters.contactMechId) {
        Map updateResult = run service: 'updatePartyEmailAddress', with: [*: parameters]
        if (!isSuccess(updateResult)) {
            return updateResult
        }
        contactMechId = updateResult.contactMechId
        logInfo("Email Contact updated emailContactMechId is ${contactMechId}")
    } else {
        Map createResult = run service: 'createPartyEmailAddress', with: [*: parameters,
                                                                          partyId: parameters.partyId ?: userLogin.partyId]
        if (!isSuccess(createResult)) {
            return createResult
        }
        contactMechId = createResult.contactMechId
        logInfo("Email Contact Created emailContactMechId is ${contactMechId}")
    }
    // entity-one sans field-map : la clé vient de la variable contactMechId du contexte
    GenericValue contactMech = from('ContactMech').where('contactMechId', contactMechId).queryOne()
    result.emailAddress = contactMech.infoString
    result.contactMechId = contactMechId
    return result
}

Map createUpdatePartyTelecomNumber() {
    Map result = success()

    String contactMechId = null
    if (parameters.contactMechId) {
        Map updateResult = run service: 'updatePartyTelecomNumber', with: [*: parameters]
        if (!isSuccess(updateResult)) {
            return updateResult
        }
        contactMechId = updateResult.contactMechId
        logInfo("Phone Contact updated phoneContactMechId is ${contactMechId}")
    } else {
        Map createResult = run service: 'createPartyTelecomNumber', with: [*: parameters]
        if (!isSuccess(createResult)) {
            return createResult
        }
        contactMechId = createResult.contactMechId
        logInfo("Phone Contact created phoneContactMechId is ${contactMechId}")
    }
    result.contactMechId = contactMechId
    return result
}

Map createUpdatePartyPostalAddress() {
    Map result = success()

    String contactMechId
    if (parameters.contactMechId) {
        Map updateResult = run service: 'updatePartyPostalAddress', with: [*: parameters]
        if (!isSuccess(updateResult)) {
            return updateResult
        }
        contactMechId = updateResult.contactMechId
        logInfo("Postal address updated, contactMechId is ${contactMechId}")
    } else {
        Map createResult = run service: 'createPartyPostalAddress', with: [*: parameters]
        if (!isSuccess(createResult)) {
            return createResult
        }
        contactMechId = createResult.contactMechId
        logInfo("Postal address created, contactMechId is ${contactMechId}")
    }
    result.contactMechId = contactMechId
    return result
}
