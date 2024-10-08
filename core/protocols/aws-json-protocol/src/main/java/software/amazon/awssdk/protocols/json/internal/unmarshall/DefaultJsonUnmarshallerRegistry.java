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

package software.amazon.awssdk.protocols.json.internal.unmarshall;

import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.core.protocol.MarshallLocation;
import software.amazon.awssdk.core.protocol.MarshallingType;
import software.amazon.awssdk.protocols.core.AbstractMarshallingRegistry;

/**
 * Registry of {@link JsonUnmarshaller} implementations by location and type.
 */
@SdkInternalApi
public final class DefaultJsonUnmarshallerRegistry extends AbstractMarshallingRegistry implements JsonUnmarshallerRegistry {

    private DefaultJsonUnmarshallerRegistry(Builder builder) {
        super(builder);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> JsonUnmarshaller<Object> getUnmarshaller(MarshallLocation marshallLocation, MarshallingType<T> marshallingType) {
        return (JsonUnmarshaller<Object>) get(marshallLocation, marshallingType);
    }

    /**
     * Returns a builder to modify this registry.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return Builder instance to construct a {@link DefaultJsonUnmarshallerRegistry}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for a {@link DefaultJsonUnmarshallerRegistry}.
     */
    public static final class Builder extends AbstractMarshallingRegistry.Builder {

        private Builder(DefaultJsonUnmarshallerRegistry unmarshallerRegistry) {
            super(unmarshallerRegistry);
        }

        private Builder() {
        }

        public <T> Builder payloadUnmarshaller(MarshallingType<T> marshallingType,
                                               JsonUnmarshaller<T> marshaller) {
            register(MarshallLocation.PAYLOAD, marshallingType, marshaller);
            return this;
        }

        public <T> Builder headerUnmarshaller(MarshallingType<T> marshallingType,
                                              JsonUnmarshaller<T> marshaller) {
            register(MarshallLocation.HEADER, marshallingType, marshaller);
            return this;
        }

        public <T> Builder statusCodeUnmarshaller(MarshallingType<T> marshallingType,
                                                  JsonUnmarshaller<T> marshaller) {
            register(MarshallLocation.STATUS_CODE, marshallingType, marshaller);
            return this;
        }

        /**
         * @return An immutable {@link DefaultJsonUnmarshallerRegistry} object.
         */
        public DefaultJsonUnmarshallerRegistry build() {
            return new DefaultJsonUnmarshallerRegistry(this);
        }
    }
}
