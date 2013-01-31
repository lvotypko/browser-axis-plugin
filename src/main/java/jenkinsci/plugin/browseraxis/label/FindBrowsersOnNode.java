package jenkinsci.plugin.browseraxis.label;

import groovyjarjarasm.asm.Label;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.LabelFinder;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.slaves.SlaveComputer;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkinsci.plugin.browseraxis.Browser;
import jenkinsci.plugin.browseraxis.BrowserVersion;

/**
 * Find browsers, versions for nodes and actualize labels if it is necessary
 * 
 * @author Lucie Votypkova
 */
public class FindBrowsersOnNode extends Thread {

    private long startTime;
    private Node node;
    private Logger LOGGER = Logger.getLogger(Hudson.class.getName());

    public FindBrowsersOnNode(String name, long startTime, Node node) {
        super(name);
        this.startTime = startTime;
        this.node = node;
    }

    public long getStartTime() {
        return startTime;
    }
    
    public Node getNode(){
        return node;
    }

    @Override
    public void run() {
        Set<String> atoms = new TreeSet<String>();
        Browser.DescriptorImpl descriptor = ((Browser.DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(Browser.class));
        Set<Browser> browsers = descriptor.getBrowsers();
        for (Browser browser : browsers) {
            boolean contains = false;
            LabelAtom atom = getAutoversion(browser, node); //look for default version of this browser

            if (atom != null) {
                contains = true;
                atoms.add(atom.getDisplayName());
            }
            for (BrowserVersion version : browser.getNotAutoCreatedVersions()) {
                try {
                    if (node.createPath(parsePath(node.toComputer(), version.getPath(node.toComputer(), browser))).exists()) {
                        contains = true;
                        atoms.add((version.getVersionName()));
                    }
                } catch (IOException ex) {
                    Logger.getLogger(BrowserFinder.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InterruptedException ex) {
                    Logger.getLogger(BrowserFinder.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (contains) {
                atoms.add(browser.getName());
            }
        }
        descriptor.getSlaveLables().put(node.getDisplayName(), atoms); //actualize stored information about node`s browser labels
        BrowserFinder finder = LabelFinder.all().get(BrowserFinder.class);
        finder.actualizeNode(node.getDisplayName(), System.currentTimeMillis()); // store time of actualization
        //actualization of labels if it is needed
        for (LabelAtom label : descriptor.getLabelsOfNode(node)) {
            if (!label.contains(node)) {
                List<Node> nodes = Hudson.getInstance().getNodes();
                try {
                    Hudson.getInstance().setNodes(nodes); //change node, because the label is changed
                } catch (IOException ex) {
                    Logger.getLogger(FindBrowsersOnNode.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        descriptor.save();
        //BrowserFinder.getThreads().remove(this);
        node.getAssignedLabels();
    }

    /**
     * Replace system variable names by their values
     * 
     * @return path without system variables
     */
    public static String parsePath(Computer computer, String path) throws IOException, InterruptedException {
        EnvVars env = computer.getEnvironment();
        for (String s : env.keySet()) {
            if (!(path.contains("$"))) {
                return path;
            }
            String variable = "${" + s + "}";
            if (path.contains(variable)) {
                path = path.replace(variable, env.get(s));
            }
        }
        return path;
    }

    private String doCommand(Browser browser, Node node) throws IOException, InterruptedException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        TaskListener listener = new StreamTaskListener(stream);
        Shell shell = new Shell(browser.getFindVersionCommand(node.toComputer()));
        FilePath script = shell.createScriptFile(node.getRootPath());
        node.createLauncher(listener).launch().cmds(shell.buildCommandLine(script)).stdout(listener).pwd(node.getRootPath()).join();
        return stream.toString();
    }

    private String parseVersion(String version) {
        String[] lines = version.split("\n");
        int size = (lines.length) -1;
        version = lines[size]; //the last line to avoid mix it with waring messages
        if (version == null || "".equals(version)) {
            return null; // command did not return any result
        }
        if (!version.contains(".")) {
            return null; // returned result does not contains version
        }
        int index = version.indexOf(".");
        String shortVersion = version.substring(0, index);
        if (!shortVersion.matches("[0-9]+")) {
            //filter additional information
            while (shortVersion.length() != 0 && (!shortVersion.matches("[0-9]+"))) {
                shortVersion = shortVersion.substring(1);
            }
        }
        if (shortVersion.length() == 0 || shortVersion.length() > 3) {
            return null; //filtered result does not look like version
        }
        return shortVersion; //short version of default browser
    }
    
    public boolean isAutoversionPossible(Browser browser, Node node){
        if (!(node.toComputer() instanceof SlaveComputer)) {
            return false; // it is autolabeling for slave not master
        }
        SlaveComputer slave = (SlaveComputer) node.toComputer();
        boolean forwin = (browser.getAutoCreatingVersionsWindows() && (!slave.isUnix()));
        boolean forUnix = (browser.getAutoCreatingVersionsUnix() && slave.isUnix());
        if (!(forwin || forUnix)) {
            return false;// browser does not use default browser
        }
        return true;
    }

    public LabelAtom getAutoversion(Browser browser, Node node) {
        if(!isAutoversionPossible(browser, node)){
            return null;
        }
        try {
            FilePath path = node.createPath(parsePath(node.toComputer(), browser.getDefaultPath(node.toComputer())));
            if (!path.exists()) {
                return null; // there is no file in default browser path
            }
            String version = doCommand(browser, node); // find out the version of defult browser by command
            String shortVersion = parseVersion(version);
            if (shortVersion == null) {
                return null; // the result of commnad does not look as version of browser
            }
            BrowserVersion autoVersion = new BrowserVersion(browser.getName() + "-" + shortVersion + "-auto", browser.getDefaultPathWithoutSuffix(node.toComputer()), true);
            browser.addVersion(autoVersion);
            return Hudson.getInstance().getLabelAtom(autoVersion.getVersionName());
        } catch (Exception ex) {
            return null; // there was some problem during obtaining version
        }
    }
}
