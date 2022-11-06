package net.azisaba.combatmath;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import net.blueberrymc.nativeutil.ClassDefinition;
import net.blueberrymc.nativeutil.NativeUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

public class CombatMath extends JavaPlugin {
    private static final ClassPool cp = ClassPool.getDefault();
    private static String body;

    @Override
    public void onEnable() {
        Objects.requireNonNull(Bukkit.getPluginCommand("combatmath")).setExecutor(new CombatMathCommand(this));
        reload();
    }

    private void queueClass(String name, Function<String, byte[]> bytecodeProvider) {
        queueClass(name, false, bytecodeProvider);
    }

    private void queueClass(String name, boolean bypassServerCheck, Function<String, byte[]> bytecodeProvider) {
        if (!bypassServerCheck) {
            try {
                Class.forName(name.substring(0, name.lastIndexOf('.') + 1) + "MinecraftServer");
            } catch (ClassNotFoundException ex) {
                return;
            }
        }
        Class<?> clazz = getLoadedClass(name);
        if (clazz != null) {
            getLogger().info(name + " is already loaded, attempting to redefine the class");
            byte[] bytecode = bytecodeProvider.apply(name);
            NativeUtil.redefineClasses(new ClassDefinition[]{new ClassDefinition(clazz, bytecode)});
            getLogger().info(name + " has been redefined");
        } else {
            getLogger().info(name + " is not loaded, registering a class load hook");
            NativeUtil.registerClassLoadHook((classLoader, cname, redefiningClass, protectionDomain, bytes) -> {
                if (cname.replace('/', '.').equals(name)) {
                    getLogger().info("Modifying class " + name);
                    byte[] bytecode = bytecodeProvider.apply(name);
                    getLogger().info("Modified class " + name);
                    return bytecode;
                }
                return null;
            });
        }
    }

    private static byte[] rewriteCombatMathClass(String name) {
        try {
            CtClass cc = cp.get(name);
            cc.defrost();
            cc.getMethod("a", "(FFF)F").setBody(body);
            return cc.toBytecode();
        } catch (NotFoundException | CannotCompileException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] rewriteRangedAttributeClass(
            String name,
            String armorAttributeName,
            String armorToughnessAttributeName,
            String sanitizeValueMethod,
            String getNameMethod,
            String getDefaultMethod,
            String minField,
            String maxField
    ) {
        try {
            CtClass cc = cp.get(name);
            cc.defrost();
            // sanitizeValue(D)D
            cc.getMethod(sanitizeValueMethod, "(D)D").setBody(
                    "{\n" +
                            "  if (Double.isNaN($1)) {\n" +
                            "    return this." + getDefaultMethod + "();\n" +
                            "  } else {\n" +
                            "    if (this." + getNameMethod + "().equals(\"" + armorAttributeName + "\") || this." + getNameMethod + "().equals(\"" + armorToughnessAttributeName + "\")) {\n" +
                            "      return Math.max($1, this." + minField + ");\n" +
                            "    } else {\n" +
                            "      return Math.max(this." + minField + ", Math.min($1, this." + maxField + "));\n" +
                            "    }\n" +
                            "  }\n" +
                            "}"
            );
            return cc.toBytecode();
        } catch (NotFoundException | CannotCompileException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> getLoadedClass(String className) {
        for (Class<?> clazz : NativeUtil.getLoadedClasses()) {
            if (clazz.getTypeName().equals(className)) {
                return clazz;
            }
        }
        return null;
    }

    public void reload() {
        saveDefaultConfig();
        reloadConfig();
        body = Objects.requireNonNull(getConfig().getString("body", "{ return $1 * ($1 / ($2 *  0.025 + 1) / ($3 * 0.025 + 1) / $1); }"))
                .replaceAll("\\$damage", "\\$1")
                .replaceAll("\\$armorToughness", "\\$3")
                .replaceAll("\\$toughness", "\\$3")
                .replaceAll("\\$armor", "\\$2");
        getLogger().info("Using method body: \n" + body);

        // AttributeRanged
        queueClass("net.minecraft.server.v1_15_R1.AttributeRanged", name -> CombatMath.rewriteRangedAttributeClass(
                name,
                "generic.armor",
                "generic.armorToughness",
                "a",
                "getName",
                "getDefault",
                "a",
                "maximum"));
        // 1.17.1 (I don't know what do I do for 1.18+)
        queueClass("net.minecraft.world.entity.ai.attributes.AttributeRanged", name -> CombatMath.rewriteRangedAttributeClass(
                name,
                "attribute.name.generic.armor",
                "attribute.name.generic.armor_toughness",
                "a",
                "getName",
                "getDefault",
                "a",
                "c"));

        // CombatMath
        queueClass("net.minecraft.server.v1_15_R1.CombatMath", CombatMath::rewriteCombatMathClass); // 1.15.2
        queueClass("net.minecraft.server.v1_16_R3.CombatMath", CombatMath::rewriteCombatMathClass); // 1.16.5
        queueClass("net.minecraft.world.damagesource.CombatMath", true, CombatMath::rewriteCombatMathClass); // 1.17+
    }
}
