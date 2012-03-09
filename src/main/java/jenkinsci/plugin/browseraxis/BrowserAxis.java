package jenkinsci.plugin.browseraxis;

import hudson.EnvVars;
import hudson.Extension;
import hudson.matrix.LabelAxis;
import hudson.matrix.LabelExpAxis;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Node;
import java.io.IOException;
import java.io.PrintStream;
import org.kohsuke.stapler.DataBoundConstructor;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkinsci.plugin.browseraxis.label.FindBrowsersOnNode;

/**
 * Axis for browsers. It is similar to JDKAxis
 * 
 * @author Lucie Votypkova
 */
public class BrowserAxis extends LabelAxis implements BrowserVariableSetter{

    @DataBoundConstructor
    public BrowserAxis(List<String> values) {
        super("Browser", values);
    }

    @Override
    public boolean isSystem() {
        return true;
    }

    public void setEnviromentVariables(String value, EnvVars envVar, PrintStream log, Node node) {
        Browser.DescriptorImpl descriptor = ((Browser.DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(Browser.class));
        BrowserVersion version = descriptor.getBrowserVersionByName(value);
        if (version == null) {
            throw new IllegalArgumentException("Version of browser should not be null");
        }
        String path = version.getPath(node.toComputer(), descriptor.getBrowserOfVersion(version)); //get path for this node
        try {
            path = FindBrowsersOnNode.parsePath(node.toComputer(), path); //replace system variable of node by their values
            log.println("Set browser " + version.getVersionName() + " with path " + FindBrowsersOnNode.parsePath(node.toComputer(), path));
        } catch (IOException ex) {
            Logger.getLogger(BrowserAxis.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(BrowserAxis.class.getName()).log(Level.SEVERE, null, ex);
        }
        envVar.put("BROWSER_AXIS", version.getVersionName());
        envVar.put("PATH+BROWSER", path);
        if(path.contains("\\"))
            path = path.replace("\\", "/"); // due to windows path separator \, it makes mess in maven goals
        envVar.put("BROWSER_AXIS_PATH", path);
    }


    @Extension
    public static class DescriptorImpl extends LabelExpAxis.DescriptorImpl {

        @Override
        public String getDisplayName() {
            return "Browser";
        }

        public Descriptor<Browser> getBrowserDescriptor() {
            return Hudson.getInstance().getDescriptorOrDie(Browser.class);
        }      
    }
}
