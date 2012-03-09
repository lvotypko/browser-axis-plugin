package jenkinsci.plugin.browseraxis;

import hudson.model.Computer;
import hudson.model.Computer;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represent version of browser
 * 
 * @author Lucie Votypkova
 */
public final class BrowserVersion implements Comparable {

    private String versionName;
    private String path;
    private boolean autoCreated;

    public BrowserVersion(String versionName, String path, boolean autoCreated) {
        this.versionName = versionName;
        this.path = path;
        this.autoCreated = autoCreated;

    }

    public boolean isAutoCreated() {
        return autoCreated;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getPath() {
        return path;
    }

    /**
     * Create with suffix. If the browser use suffix, appropriate suffix will be add to browser 
     * version path (for Unix computer Unix suffix, for Windows computer Windows suffix), otherwise return path
     * 
     * @return Path including suffix according to browser settings and computer type
     */
    public String getPath(Computer computer, Browser browser) {
        if (computer instanceof SlaveComputer) {
            boolean isUnix = ((SlaveComputer) computer).isUnix();
            if ((!isUnix) && browser.getUseSuffixs()) {
                return (path + browser.getSuffixWindows());
            }
            try {
                if (isUnix && browser.getUseSuffixs() && browser.getUnixForSuffix().contains((String) computer.getSystemProperties().get("os.name"))) {
                    return (path + browser.getSuffixUnix());
                }
            } catch (IOException ex) {
                Logger.getLogger(BrowserVersion.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(BrowserVersion.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return path;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof BrowserVersion) {
            return versionName.equals(((BrowserVersion) object).getVersionName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return versionName.hashCode();
    }

    public int compareTo(Object o) {
        if (o instanceof BrowserVersion) {
            return versionName.compareTo(((BrowserVersion) o).getVersionName());
        }
        throw new IllegalArgumentException("Object" + o + "is not instnace of class " + getClass());
    }
}