package io.github.nbcss.createfactorycontroller.content.render;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class WorldDashboardSpritesTest {
    @Test void atlasSourcesMatchLookupAndDoNotReduceFourLevelBlockMipmaps() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/minecraft/atlases/blocks.json")) {
            assertNotNull(stream);
            var sources = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("sources");
            var paths = new HashSet<String>();
            for (var element : sources) {
                var source = element.getAsJsonObject();
                assertEquals("minecraft:single", source.get("type").getAsString());
                String sprite = source.get("sprite").getAsString();
                String path = sprite.substring("createfactorycontroller:world_dashboard/".length());
                assertTrue(paths.add(path), "Duplicate world sprite: " + path);
                assertEquals("createfactorycontroller:gui/sprites/" + path, source.get("resource").getAsString());
                try (var png = getClass().getResourceAsStream("/assets/createfactorycontroller/textures/gui/sprites/" + path + ".png")) {
                    assertNotNull(png, path);
                    var header = ByteBuffer.wrap(png.readNBytes(24));
                    int width = header.getInt(16), height = header.getInt(20);
                    assertTrue(width >= 16 && width % 16 == 0, path);
                    assertTrue(height >= 16 && height % 16 == 0, path);
                }
            }
            assertEquals(WorldDashboardSprites.PATHS, paths);
        }
    }
}
