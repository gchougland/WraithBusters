package com.hexvane.wraithbusters.arena;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import java.io.IOException;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Gson helpers for arena layout persistence. Hytale {@link Transform} defaults use NaN rotation,
 * which Gson refuses to serialize unless special-cased.
 */
public final class ArenaLayoutJson {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Transform.class, new TransformAdapter())
        .create();

    private ArenaLayoutJson() {}

    @Nonnull
    public static Gson gson() {
        return GSON;
    }

    @Nonnull
    public static String toJson(@Nonnull ArenaLayout layout) {
        return GSON.toJson(layout);
    }

    @Nonnull
    public static ArenaLayout fromJson(@Nonnull String text) {
        ArenaLayout layout = GSON.fromJson(text, ArenaLayout.class);
        if (layout == null) {
            throw new IllegalStateException("Arena layout JSON was empty");
        }
        return layout;
    }

    private static final class TransformAdapter extends TypeAdapter<Transform> {
        @Override
        public void write(@Nonnull JsonWriter out, Transform value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            Vector3d position = value.getPosition();
            Rotation3f rotation = value.getRotation();
            out.beginObject();
            out.name("position");
            writeVector(out, position.x, position.y, position.z);
            if (hasRotation(rotation)) {
                out.name("rotation");
                out.beginObject();
                if (!Float.isNaN(rotation.pitch())) {
                    out.name("x").value(rotation.pitch());
                }
                if (!Float.isNaN(rotation.yaw())) {
                    out.name("y").value(rotation.yaw());
                }
                if (!Float.isNaN(rotation.roll())) {
                    out.name("z").value(rotation.roll());
                }
                out.endObject();
            }
            out.endObject();
        }

        @Override
        public Transform read(@Nonnull JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return new Transform();
            }
            double x = 0;
            double y = 0;
            double z = 0;
            float pitch = Float.NaN;
            float yaw = Float.NaN;
            float roll = Float.NaN;
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "position" -> {
                        double[] pos = readVector(in);
                        x = pos[0];
                        y = pos[1];
                        z = pos[2];
                    }
                    case "rotation" -> {
                        float[] rot = readRotation(in);
                        pitch = rot[0];
                        yaw = rot[1];
                        roll = rot[2];
                    }
                    case "X" -> x = in.nextDouble();
                    case "Y" -> y = in.nextDouble();
                    case "Z" -> z = in.nextDouble();
                    case "Pitch" -> pitch = (float) in.nextDouble();
                    case "Yaw" -> yaw = (float) in.nextDouble();
                    case "Roll" -> roll = (float) in.nextDouble();
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (hasRotation(pitch, yaw, roll)) {
                return new Transform(
                    x,
                    y,
                    z,
                    Float.isNaN(pitch) ? 0f : pitch,
                    Float.isNaN(yaw) ? 0f : yaw,
                    Float.isNaN(roll) ? 0f : roll
                );
            }
            return new Transform(x, y, z);
        }

        private static boolean hasRotation(@Nonnull Rotation3f rotation) {
            return hasRotation(rotation.pitch(), rotation.yaw(), rotation.roll());
        }

        private static boolean hasRotation(float pitch, float yaw, float roll) {
            return !Float.isNaN(pitch) || !Float.isNaN(yaw) || !Float.isNaN(roll);
        }

        private static void writeVector(@Nonnull JsonWriter out, double x, double y, double z) throws IOException {
            out.beginObject();
            out.name("x").value(x);
            out.name("y").value(y);
            out.name("z").value(z);
            out.endObject();
        }

        @Nonnull
        private static double[] readVector(@Nonnull JsonReader in) throws IOException {
            double x = 0;
            double y = 0;
            double z = 0;
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "x", "X" -> x = in.nextDouble();
                    case "y", "Y" -> y = in.nextDouble();
                    case "z", "Z" -> z = in.nextDouble();
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return new double[] { x, y, z };
        }

        @Nonnull
        private static float[] readRotation(@Nonnull JsonReader in) throws IOException {
            float pitch = Float.NaN;
            float yaw = Float.NaN;
            float roll = Float.NaN;
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "x", "Pitch" -> pitch = (float) in.nextDouble();
                    case "y", "Yaw" -> yaw = (float) in.nextDouble();
                    case "z", "Roll" -> roll = (float) in.nextDouble();
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return new float[] { pitch, yaw, roll };
        }
    }
}
