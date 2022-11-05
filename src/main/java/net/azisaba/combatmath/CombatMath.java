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

public class CombatMath extends JavaPlugin {
    private static final ClassPool cp = ClassPool.getDefault();
    private static String body;

    @Override
    public void onEnable() {
        Objects.requireNonNull(Bukkit.getPluginCommand("combatmath")).setExecutor(new CombatMathCommand(this));
        reload();
    }

    private void queueClass(String name) {
        queueClass(name, false);
    }

    private void queueClass(String name, boolean bypassServerCheck) {
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
            byte[] bytecode = rewriteClass(name);
            NativeUtil.redefineClasses(new ClassDefinition[]{new ClassDefinition(clazz, bytecode)});
            getLogger().info(name + " has been redefined");
        } else {
            getLogger().info(name + " is not loaded, registering a class load hook");
            NativeUtil.registerClassLoadHook((classLoader, cname, redefiningClass, protectionDomain, bytes) -> {
                if (cname.replace('/', '.').equals(name)) {
                    getLogger().info("Modifying class " + name);
                    byte[] bytecode = rewriteClass(name);
                    getLogger().info("Modified class " + name);
                    return bytecode;
                }
                return null;
            });
        }
    }

    private static byte[] rewriteClass(String name) {
        try {
            CtClass cc = cp.get(name);
            cc.defrost();
            cc.getMethod("a", "(FFF)F").setBody(body);
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
        queueClass("net.minecraft.server.v1_15_R1.CombatMath"); // 1.15.2
        queueClass("net.minecraft.server.v1_16_R3.CombatMath"); // 1.16.5
        queueClass("net.minecraft.world.damagesource.CombatMath", true); // 1.17+
    }
}
