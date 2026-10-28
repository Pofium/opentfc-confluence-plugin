package org.opentfc.diag.lite;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Резервный способ найти каталог журналов без OSGi-сервисов: системное свойство
 * confluence.home (задаётся через confluence-init.properties). Путь нормализуется,
 * принимается только существующий каталог с именем "logs".
 */
final class HomeDirProbe {

    private HomeDirProbe() {
    }

    static Path logsDirFromSysProp() {
        String sysHome = System.getProperty("confluence.home");
        if (sysHome == null || sysHome.isEmpty()) {
            return null;
        }
        try {
            Path dir = Path.of(sysHome, "logs").normalize();
            if (Files.isDirectory(dir)
                    && dir.getFileName() != null && "logs".equals(dir.getFileName().toString())) {
                return dir;
            }
        } catch (Throwable ignored) {
            // недоступно — вернём null
        }
        return null;
    }
}
