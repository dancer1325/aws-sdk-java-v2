/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.core.traits;

import software.amazon.awssdk.annotations.SdkProtectedApi;

/**
 * Trait that indicates a String member is a JSON document. This can influence how it is marshalled/unmarshalled. For example,
 * a string bound to the header with this trait applied will be Base64 encoded.
 */
@SdkProtectedApi
public final class JsonValueTrait implements Trait {

    private JsonValueTrait() {
    }

    public static JsonValueTrait create() {
        return new JsonValueTrait();
    }

    @Override
    public TraitType type() {
        return TraitType.JSON_VALUE_TRAIT;
    }
}
