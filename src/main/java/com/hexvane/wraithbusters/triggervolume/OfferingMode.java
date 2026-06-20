package com.hexvane.wraithbusters.triggervolume;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.exception.CodecException;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.util.RawJsonReader;
import java.io.IOException;
import javax.annotation.Nonnull;
import org.bson.BsonValue;

public enum OfferingMode {
    ORDERED,
    UNORDERED;

    /**
     * Saved trigger volumes use legacy SCREAMING_SNAKE names ({@code UNORDERED}, {@code ORDERED}).
     * Editor schema uses camel-case labels via {@link EnumCodec}.
     */
    public static final Codec<OfferingMode> CODEC = new Codec<>() {
        private static final EnumCodec<OfferingMode> EDITOR_SCHEMA = new EnumCodec<>(OfferingMode.class);

        @Nonnull
        @Override
        public OfferingMode decode(@Nonnull BsonValue bsonValue, ExtraInfo extraInfo) {
            return decodeValue(Codec.STRING.decode(bsonValue, extraInfo));
        }

        @Nonnull
        @Override
        public BsonValue encode(@Nonnull OfferingMode mode, ExtraInfo extraInfo) {
            return Codec.STRING.encode(mode.name(), extraInfo);
        }

        @Nonnull
        @Override
        public OfferingMode decodeJson(@Nonnull RawJsonReader reader, ExtraInfo extraInfo) throws IOException {
            return decodeValue(Codec.STRING.decodeJson(reader, extraInfo));
        }

        @Nonnull
        @Override
        public Schema toSchema(@Nonnull SchemaContext context) {
            return EDITOR_SCHEMA.toSchema(context);
        }

        @Nonnull
        private static OfferingMode decodeValue(@Nonnull String value) {
            for (OfferingMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            String[] keys = EDITOR_SCHEMA.getEnumKeys();
            OfferingMode[] modes = values();
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(value)) {
                    return modes[i];
                }
            }
            throw new CodecException("Failed to find enum value for " + value);
        }
    };
}
