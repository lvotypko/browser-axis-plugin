package jenkinsci.plugin.browseraxis;

import hudson.Extension;
import hudson.slaves.SlaveComputer;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.LabelFinder;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import java.util.concurrent.ConcurrentHashMap;
import jenkinsci.plugin.browseraxis.label.BrowserFinder;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Represent Browser and its setting
 * 
 * @author Lucie Votypkova
 */
public class Browser implements Describable<Browser>, Comparable {

    private String name;
    Set<BrowserVersion> versions;
    private String suffixUnix;
    private String suffixWindows;
    private boolean useSuffixs = false;
    private Set<String> unixsForSuffix = new TreeSet<String>(); //unix os which uses unix suffix
    private boolean autoCreatingVersionsWindows = false;
    private boolean autoCreatingVersionsUnix = false;
    private String defaultPathWindows;
    private String defaultPathUnix;
    private String findVersionCommandUnix;
    private String findVersionCommandWindows;

    @DataBoundConstructor
    public Browser(String name, Set<BrowserVersion> version, boolean useSuffixs, String suffixWindows, String suffixUnix, Set<String> unixOsForSuffix,
            boolean autoCreatingVersionsWindows, boolean autoCreatingVersionsUnix, String defaultPathWindows, String defaultPathUnix, String findVersionCommandUnix, String findVersionCommandWindows) {
        this.name = name;
        this.versions = version;
        this.suffixUnix = suffixUnix;
        this.suffixWindows = suffixWindows;
        this.useSuffixs = useSuffixs;
        this.unixsForSuffix = unixOsForSuffix;
        this.autoCreatingVersionsWindows = autoCreatingVersionsWindows;
        this.autoCreatingVersionsUnix = autoCreatingVersionsUnix;
        this.defaultPathWindows = defaultPathWindows;
        this.defaultPathUnix = defaultPathUnix;
        this.findVersionCommandUnix = findVersionCommandUnix;
        this.findVersionCommandWindows = findVersionCommandWindows;
    }

    public String getFindVersionCommand(Computer computer) {
        if (computer instanceof SlaveComputer) {
            if (((SlaveComputer) computer).isUnix()) {
                return findVersionCommandUnix;
            } else {
                return findVersionCommandWindows;
            }
        }
        return null;
    }

    /**
     * Return path of locally installed version of browser on given computer which include defined suffix
     * 
     * @param computer
     * @return Path of locally installed version of browser
     */
    public String getDefaultPath(Computer computer) throws InterruptedException, IOException {
        if (computer instanceof SlaveComputer) {
            if (((SlaveComputer) computer).isUnix()) {
                if (useSuffixs && unixsForSuffix.contains((String) computer.getSystemProperties().get("os.name"))) {
                    return (defaultPathUnix + suffixUnix);
                }
                return defaultPathUnix;
            } else {
                if (useSuffixs) {
                    return (defaultPathWindows + suffixWindows);
                }
                return defaultPathWindows;
            }
        }
        return null;
    }
    
    /**
     * Add new version
     * 
     */
    public void addVersion(BrowserVersion version) {
        versions.add(version);
        DescriptorImpl des = (DescriptorImpl) getDescriptor();
        des.getMapBrowsers().put(this, versions);
    }

     /**
      * Return path of locally installed version of browser for given computer without suffix
      * 
      * @return Path of locally installed version of browser
      * 
      */
    public String getDefaultPathWithoutSuffix(Computer computer) throws InterruptedException, IOException {
        if (computer instanceof SlaveComputer) {
            if (((SlaveComputer) computer).isUnix()) {
                return defaultPathUnix;
            } else {
                return defaultPathWindows;
            }
        }
        return null;
    }

    public String getFindVersionCommandWindows() {
        return findVersionCommandWindows;
    }

    public String getFindVersionCommandUnix() {
        return findVersionCommandUnix;
    }

    public String getDefaultPathUnix() {
        return defaultPathUnix;
    }

    public String getDefaultPathWindows() {
        return defaultPathWindows;
    }

    public boolean getAutoCreatingVersionsWindows() {
        return autoCreatingVersionsWindows;
    }

    public boolean getAutoCreatingVersionsUnix() {
        return autoCreatingVersionsUnix;
    }

    public String getName() {
        return name;
    }

    public boolean getUseSuffixs() {
        return useSuffixs;
    }

    public Set<String> getUnixForSuffix() {
        return unixsForSuffix;
    }

    public String getSuffixWindows() {
        return suffixWindows;
    }

    public String getSuffixUnix() {
        return suffixUnix;
    }

    public Set<BrowserVersion> getVersions() {
        return versions;
    }

    public Set<BrowserVersion> getNotAutoCreatedVersions() {
        Set<BrowserVersion> setVersions = new TreeSet<BrowserVersion>();
        for (BrowserVersion version : versions) {
            if (!version.isAutoCreated()) {
                setVersions.add(version);
            }
        }
        return setVersions;
    }

    public Descriptor<Browser> getDescriptor() {
        return (Descriptor<Browser>) Hudson.getInstance().getDescriptor(getClass());
    }

    public int compareTo(Object object) {
        if (object instanceof Browser) {
            return name.compareTo(((Browser) object).getName());
        }
        throw new IllegalArgumentException("Object is not instance of class " + getClass());
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Browser) {
            return name.equals(((Browser) object).getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Browser> {

        private Map<Browser, Set<BrowserVersion>> browsers = new TreeMap<Browser, Set<BrowserVersion>>();
        private static Set<String> unixOS = new TreeSet<String>();
        // Name of slaves and their browser labels
        // Map of slaves nad thir browser labels are saved here for performance purposes.
        private Map<String, Set<String>> slaveLabels = new ConcurrentHashMap<String, Set<String>>();

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            Map<Browser, Set<BrowserVersion>> newBrowsers = new TreeMap<Browser, Set<BrowserVersion>>();
            Object o = json.get("browser");
            if (o != null) {
                if (o instanceof JSONObject) {
                    setBrowser(((JSONObject) o), newBrowsers);
                } else {
                    for (Object browser : json.getJSONArray("browser")) {
                        setBrowser(((JSONObject) browser), newBrowsers);
                    }
                }
            }
            browsers = newBrowsers;
            checkNodes(); //delete unused nodes
            BrowserFinder finder = LabelFinder.all().get(BrowserFinder.class);
            finder.setActualizationOfBrowsers(); // nodes will actualize their browser labes according to new settings
            save();
            return true;
        }

        public Map<String, Set<String>> getSlaveLables() {
            return slaveLabels;
        }
        
        /**
         * Check compatibility with names of nodes with nodes contained in Hudson
         * 
         * @return set of browsers
         * 
         */
        public void checkNodes() {
            if (Hudson.getInstance().getNodes().size() + 1 < slaveLabels.keySet().size()) {
                deleteUnusedNodes();
            }
        }

        /**
         * Delete nodes which does not exist.
         * 
         */
        public void deleteUnusedNodes() {
            Iterator<Entry<String, Set<String>>> it = slaveLabels.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Set<String>> st = it.next();
                if (Hudson.getInstance().getNode(st.getKey()) == null && (!Hudson.getInstance().getDisplayName().equals(st.getKey()))) {
                    it.remove();
                }
            }
        }

        /**
         * Return all kinds of Unix systems, which has ever been on Hudson slaves
         * 
         * @return set of names of Unix systems
         */
        public static Set<String> getUnixOs() {
            if (unixOS == null) {
                return getConnectedUnixOs();
            }
            unixOS.addAll(getConnectedUnixOs());
            return unixOS;
        }

        /**
         * Set versions of browser from request parameters
         * 
         */
        public void setVersion(JSONObject json, Set<BrowserVersion> versions) {
            Object object = json.get("version");
            if (object != null) {
                if (object instanceof JSONObject) {
                    versions.add(new BrowserVersion(((JSONObject) object).getString("versionName"), ((JSONObject) object).getString("path"), false));
                } else {
                    for (Object version : (JSONArray) object) {
                        versions.add(new BrowserVersion(((JSONObject) version).getString("versionName"), ((JSONObject) version).getString("path"), false));
                    }
                }
            }
        }
        
        /**
         * Set browsers from request parameters
         * 
         */
        public void setBrowser(JSONObject json, Map<Browser, Set<BrowserVersion>> newBrowsers) {
            Set<BrowserVersion> versions = new TreeSet<BrowserVersion>();
            String type = json.getString("name");
            boolean useSuffixs = json.getBoolean("useSuffixs");
            String suffixUnix = null;
            String suffixWindows = null;
            Set<String> unixsForSuffix = new TreeSet<String>();
            if (useSuffixs) {
                suffixUnix = json.getString("suffixUnix");
                Object obj = json.get("values"); // get unix system which will use suffix
                if (obj != null) {
                    if (obj instanceof String) {
                        unixsForSuffix.add((String) obj);
                    }
                    if (obj instanceof JSONArray) {
                        for (Object o : (JSONArray) obj) {
                            unixsForSuffix.add((String) o);
                        }
                    }
                }
            }
            if (useSuffixs) {
                suffixWindows = json.getString("suffixWindows");
            }
            boolean autoCreatingVersionsWindows = json.getBoolean("autoCreatingVersionsWindows");
            String defaultPathWindows = null;
            String findVersionCommandWindows = null;
            if (autoCreatingVersionsWindows) {
                defaultPathWindows = json.getString("defaultPathWindows");
                findVersionCommandWindows = json.getString("findVersionCommandWindows");
            }
            boolean autoCreatingVersionsUnix = json.getBoolean("autoCreatingVersionsUnix");
            String defaultPathUnix = null;
            String findVersionCommandUnix = null;
            if (autoCreatingVersionsUnix) {
                defaultPathUnix = json.getString("defaultPathUnix");
                findVersionCommandUnix = json.getString("findVersionCommandUnix");
            }
            versions = new TreeSet<BrowserVersion>();
            setVersion(json, versions);
            Browser b = new Browser(type, versions, useSuffixs, suffixWindows, suffixUnix, unixsForSuffix, autoCreatingVersionsWindows, autoCreatingVersionsUnix, defaultPathWindows, defaultPathUnix, findVersionCommandUnix, findVersionCommandWindows);
            newBrowsers.put(b, b.getVersions());
        }

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.errorWithMarkup("Please set a name");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckPath(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.errorWithMarkup("Please set a name");
            }
            File file = new File(value);
            if (!(file.exists())) {
                return FormValidation.warning("This path does not exist");
            }
            return FormValidation.ok();
        }

        /**
         * Return all kinds of browser which is defined
         * 
         * @return set of browsers
         * 
         */
        public Set<Browser> getBrowsers() {
            return browsers.keySet();
        }

        /**
         * Return map of kinds of browsers and their versions
         * 
         * @return map of browsers and their versions
         * 
         */
        public Map<Browser, Set<BrowserVersion>> getMapBrowsers() {
            return browsers;
        }

        /**
         * Return all kinds of browser which is defined
         * 
         * @return set of browsers
         * 
         */
        public BrowserVersion getBrowserVersionByName(String name) {
            BrowserVersion version = null;
            for (Set<BrowserVersion> versions : browsers.values()) {
                for (BrowserVersion v : versions) {
                    if (v.getVersionName().equals(name)) {
                        version = v;
                    }
                }
            }
            return version;
        }

        /**
         * Get browser labels for given node. If the slave is not contained this node add it with empty set of browser labels
         * 
         * @return set of browser labels
         * 
         */
        public Set<LabelAtom> getLabelsOfNode(Node node) {
            if (!slaveLabels.containsKey(node.getDisplayName())) {
                slaveLabels.put(node.getDisplayName(), new TreeSet<String>());
            }
            Set<LabelAtom> atoms = new TreeSet<LabelAtom>();
            for (String st : slaveLabels.get(node.getDisplayName())) {
                atoms.add(Hudson.getInstance().getLabelAtom(st));
            }
            return atoms;
        }

        /**
         * Find browser by its name
         * 
         * @return Browser with give name
         * 
         */
        public Browser findBrowserByName(String name) {
            Browser browser = null;
            for (Browser b : getBrowsers()) {
                if (b.getName().equals(name)) {
                    browser = b;
                }
            }
            return browser;
        }

         /**
         * Find all versions of all browsers
         * 
         * @return List of all versions
         * 
         */
        public List<BrowserVersion> getAllBrowserVersions() {
            List<BrowserVersion> versions = new ArrayList<BrowserVersion>();
            for (Set<BrowserVersion> versionList : browsers.values()) {
                versions.addAll(versionList);
            }
            return versions;
        }

         /**
         * Find browser of given version
         * 
         * @return Browser of version
         * 
         */
        public Browser getBrowserOfVersion(BrowserVersion version) {
            for (Browser browser : browsers.keySet()) {
                if (browser.getVersions().contains(version)) {
                    return browser;
                }
            }
            return null;
        }

        @Override
        public String getDisplayName() {
            return "browser";
        }

         /**
         * Find all kinds of Unix systems which is installed on currently connected slaves
         * 
         * @return Set of Unix systems
         * 
         */
        public static SortedSet<String> getConnectedUnixOs() {
            SortedSet<String> unixOs = new TreeSet<String>();
            for (Node node : Hudson.getInstance().getNodes()) {
                if (node.toComputer() != null) {
                    if (node.toComputer() instanceof SlaveComputer && node.toComputer().isOnline()) {
                        SlaveComputer slave = (SlaveComputer) node.toComputer();
                        if (slave.isUnix()) {
                            try {
                                String string = (String) slave.getSystemProperties().get("os.name");
                                unixOs.add(string);
                            } catch (IOException ex) {
                                Logger.getLogger(Browser.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Browser.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
            }
            return unixOs;
        }
    }
}
