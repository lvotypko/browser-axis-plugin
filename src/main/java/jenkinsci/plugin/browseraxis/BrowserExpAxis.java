package jenkinsci.plugin.browseraxis;


import antlr.ANTLRException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.matrix.LabelExpAxis;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.LabelFinder;
import hudson.model.Node;
import jenkinsci.plugin.browseraxis.label.BrowserFinder;
import jenkinsci.plugin.browseraxis.label.FindBrowsersOnNode;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Expression for browsers. It is similar to LabelExpAxis, but it is only for browser labels
 * 
 * @author Lucie Votypkova
 */
public class BrowserExpAxis extends LabelExpAxis implements BrowserVariableSetter{

    @DataBoundConstructor
    public BrowserExpAxis(String values) {
        super("Browser", removeNull(getExprValues(values)));
    }
    
    public BrowserExpAxis(String name, List<String> values) {
        super(name, values);
    }

    public static List<String> removeNull(List<String> values) {
        List<String> list = new ArrayList<String>();
        for (String st : values) {
            if (st != null) {
                list.add(st);
            }
        }
        return list;
    }

    @Override
    public boolean isSystem() {
        return true;
    }
    

    public void setEnviromentVariables(String value, EnvVars envVar, PrintStream log, Node node){       
        Label label = null;
        try {
            label = Label.parseExpression(value);
        } catch (ANTLRException ex) {
            Logger.getLogger(BrowserAxis.class.getName()).log(Level.SEVERE, null, ex);
        }
        BrowserVersion version = getBrowserVersion(label, node);
        String path = version.getPath(node.toComputer(), ((Browser.DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(Browser.class)).getBrowserOfVersion(version));
        try {
            path = FindBrowsersOnNode.parsePath(node.toComputer(), path);
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

    /**
     * Find version of browser for given label expression and node
     * 
     * 
     * @return Label which suits given expression
     * @throws IllegalArgumentException if there is not any version for given label expression and node
     */
    private BrowserVersion getBrowserVersion(Label label, Node node) {
        Browser.DescriptorImpl descriptor = (Browser.DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(Browser.class);
        BrowserFinder finder = LabelFinder.all().get(BrowserFinder.class);
        LabelAtom atom = findLabelAtomForExpression(label, finder.findLabels(node));//find the first label which is contained in node`s labels and match label expression
        BrowserVersion version = (descriptor).getBrowserVersionByName(atom.getName());
        if (version == null) {
            Browser browser = (descriptor.findBrowserByName(atom.getName()));
            if (browser == null) {
                throw new IllegalArgumentException("There is not browser with label " + atom.getName() + " but should be, it is probably bug");
            }
            version = (descriptor.getBrowserVersionByName(findLabelAtomForBrowser(browser, node).getName()));
        }
        return version;
    }

    /**
     * Find the first label in collection of labels which suits given Label expression
     * 
     * 
     * @return Label which suits given expression
     * @throws IllegalArgumentException if there is not any label in collection of labels which suits given expression
     */
    private LabelAtom findLabelAtomForExpression(Label expression, Collection<LabelAtom> atoms) {
        for (LabelAtom a : atoms) {
            if (matchLabel(expression, a)) {
                return a;
            }
        }
        throw new IllegalArgumentException("There not browser for epression " + expression.getDisplayName() + " but should be, it is probably bug");
    }

    /**
     * Find the first version of browser with label which is assigned to given node
     * 
     * 
     * @return Label which suits given expression
     * @throws IllegalArgumentException if there is not any version of browser with label which is assigned to given node
     */
    private LabelAtom findLabelAtomForBrowser(Browser browser, Node node) {
        for (BrowserVersion version : browser.getVersions()) {
            LabelAtom atom = Hudson.getInstance().getLabelAtom(version.getVersionName());
            if (node.getAssignedLabels().contains(atom)) {
                return atom;
            }
        }
        throw new IllegalArgumentException("There not version for epression " + browser.getName());
    }

    public boolean matchLabel(Label label, final LabelAtom atom) {
        return label.matches(new VariableResolver<Boolean>() {
            public Boolean resolve(String string) {
                return atom.getName().equals(string);
            }
        });
    }

    @Extension
    public static class DescriptorImpl extends LabelExpAxis.DescriptorImpl {

        private String wrongExpression;

        @Override
        public String getDisplayName() {
            return "Browser expession";
        }

        public Descriptor<Browser> getBrowserDescriptor() {
            return Hudson.getInstance().getDescriptorOrDie(Browser.class);
        }

        @Override
        public BrowserExpAxis newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (wrongExpression != null) {
                String errorMessage = "Browser expression " + wrongExpression + " does not match any browser";
                if (wrongExpression.equals("")) {
                    errorMessage = "Browser expression is empty";
                }
                throw new FormException(errorMessage, "axis.name");
            }       
            return req.bindJSON(BrowserExpAxis.class, formData);

        }

        public boolean matchLabel(Label label, final LabelAtom atom) {
            if (atom == null || label == null) {
                return false;
            }
            return label.matches(new VariableResolver<Boolean>() {

                public Boolean resolve(String string) {
                    if (atom.getName() == null) {
                        return false;
                    }
                    return atom.getName().equals(string);
                }
            });
        }

        public FormValidation doCheckLabelExpr(@QueryParameter String value) {
            List<String> list = removeNull(getExprValues(value));
            if (list.isEmpty()) {
                wrongExpression = "";
                return FormValidation.error("There is not any expression or is incorrect");
            }
            for (String expression : list) {
                if (!matchExpressionAnyBrowser(expression)) {
                    wrongExpression = expression;
                    return FormValidation.error(expression + " expression does not match any browser");
                }
            }
            wrongExpression = null;
            return FormValidation.ok();
        }

        private boolean matchExpressionAnyBrowser(String expression) {
            if (expression == null) {
                return true;
            }
            Label label = null;
            try {
                label = Label.parseExpression(expression);
            } catch (ANTLRException ex) {
                Logger.getLogger(BrowserExpAxis.class.getName()).log(Level.SEVERE, null, ex);
            }
            Browser.DescriptorImpl descriptor = ((Browser.DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(Browser.class));
            Map<Browser, Set<BrowserVersion>> map = descriptor.getMapBrowsers();
            for (Browser browser : map.keySet()) {
                LabelAtom atom = Hudson.getInstance().getLabelAtom(browser.getName());
                if (matchLabel(label, atom)) {
                    return true;
                }
            }
            for (Set<BrowserVersion> versions : map.values()) {
                for (BrowserVersion version : versions) {
                    LabelAtom atom = Hudson.getInstance().getLabelAtom(version.getVersionName());
                    if (matchLabel(label, atom)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
