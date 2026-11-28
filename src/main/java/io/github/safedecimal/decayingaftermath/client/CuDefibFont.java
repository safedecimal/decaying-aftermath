package io.github.safedecimal.decayingaftermath.client;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** CU Retro GamingPix face and original TMP metrics, not Minecraft digits squeezed into its panel. */
public final class CuDefibFont {
    private record Glyph(int x,int y,int w,int h,float width,float height,float bx,float by,float advance) {}
    private record Data(float point,float ascent,float descent,int width,int height,Map<Integer,Glyph> glyphs) {}
    private static final Data DATA=load();
    private CuDefibFont() {}
    private static Data load() {
        try(var input=CuDefibFont.class.getResourceAsStream("/assets/abjection/cu_defib_font.json")) {
            if(input==null)throw new IllegalStateException("Missing original CU defibrillator font");
            var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<Integer,Glyph> glyphs=new HashMap<>();
            for(var entry:json.getAsJsonObject("glyphs").entrySet()) {
                var g=entry.getValue().getAsJsonObject();
                glyphs.put(Integer.parseInt(entry.getKey()),new Glyph(g.get("x").getAsInt(),g.get("y").getAsInt(),
                        g.get("w").getAsInt(),g.get("h").getAsInt(),g.get("width").getAsFloat(),g.get("height").getAsFloat(),
                        g.get("bearing_x").getAsFloat(),g.get("bearing_y").getAsFloat(),g.get("advance").getAsFloat()));
            }
            return new Data(json.get("point_size").getAsFloat(),json.get("ascent").getAsFloat(),json.get("descent").getAsFloat(),
                    json.get("atlas_width").getAsInt(),json.get("atlas_height").getAsInt(),Map.copyOf(glyphs));
        } catch(Exception error) {throw new IllegalStateException("Cannot read CU defibrillator font",error);}
    }
    public static boolean supports(String text) {return text.codePoints().allMatch(c->c=='\n'||DATA.glyphs.containsKey(c));}
    public static float width(String text,float size) {
        float result=0;for(int c:text.codePoints().toArray()) {var glyph=DATA.glyphs.get(c);if(glyph!=null)result+=glyph.advance*size/DATA.point;}
        return result;
    }
    /** Source horizontal alignments: 1 left, 2 centre, 4 right. Auto-size is disabled in both CU prefabs. */
    public static boolean draw(GuiGraphicsExtractor g,String text,float cx,float cy,float size,float rectWidth,int color,int alignment) {
        if(!supports(text))return false;
        String[] lines=text.split("\n",-1);float factor=size/DATA.point,step=153.99731f*factor;
        var texture=Identifier.fromNamespaceAndPath("abjection","textures/gui/defib/retro_gaming.png");
        for(int i=0;i<lines.length;i++) {
            float lineWidth=width(lines[i],size),x=switch(alignment) {
                case 1 -> cx-rectWidth/2;
                case 4 -> cx+rectWidth/2-lineWidth;
                default -> cx-lineWidth/2;
            };
            float y=cy+(lines.length-1)*step/2-i*step;
            for(int c:lines[i].codePoints().toArray()) {
                var glyph=DATA.glyphs.get(c);
                if(glyph.width>0&&glyph.height>0) {
                    int left=Math.round(128+(x+glyph.bx*factor)*.25f);
                    int top=Math.round(128-y*.25f+((DATA.ascent+DATA.descent)/2-glyph.by)*factor*.25f);
                    g.blit(RenderPipelines.GUI_TEXTURED,texture,left,top,glyph.x,glyph.y,
                            Math.max(1,Math.round(glyph.width*factor*.25f)),Math.max(1,Math.round(glyph.height*factor*.25f)),
                            glyph.w,glyph.h,DATA.width,DATA.height,color);
                }
                x+=glyph.advance*factor;
            }
        }
        return true;
    }
}
