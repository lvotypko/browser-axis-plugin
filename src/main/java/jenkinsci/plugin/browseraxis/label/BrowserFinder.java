package jenkinsci.plugin.browseraxis.label;

import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.LabelFinder;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkinsci.plugin.browseraxis.Browser;

/**
 * Add browser labels to computer and actualize them if it is necessary
 * 
 * @author Lucie Votypkova
 */
@Extension
public class BrowserFinder extends LabelFinder {

    private Map<String, Long> nodeActualization = new TreeMap<String, Long>();

    public BrowserFinder() {
    }


    public void actualizeNode(String nodeName, long time) {
        nodeActualization.put(nodeName, time);
    }

    public void checkNodes(Node node) {
        if (!(nodeActualization.containsKey(node.getDisplayName()))) {
            nodeActualization.put(node.getDisplayName(), 0l);
        }
        if (Hudson.getInstance().getNodes().size() + 1 < nodeActualization.keySet().size()) {
            deleteUnusedNodes();
        }
    }

    public void doBrowserActualization(Node node) {
        String threadName = "browsers for " + node.getDisplayName();
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread t : threads) {
            if (threadName.equals(t.getName()) && t instanceof FindBrowsersOnNode){
                // Do not create another thread for task which is proccessed
                if ((((FindBrowsersOnNode)t).getStartTime() + 300000l) < System.currentTimeMillis()) {
                    Logger.getLogger(BrowserFinder.class.getName()).log(Level.SEVERE, ("Thread created by Browser plugin to find browsers for node " + node.getDisplayName() + " is time out"));
                    t.interrupt(); // try to interupt thread which run too long 
                }
                return;
            }
        }
            FindBrowsersOnNode thread = new FindBrowsersOnNode(threadName, System.currentTimeMillis(), node);
            thread.start();
    }

    @Override
    public Collection<LabelAtom> findLabels(Node node) {
        checkNodes(node);
        Set<LabelAtom> labels = new TreeSet<LabelAtom>();
        if (node.toComputer() == null) {
            return labels;
        }
        Browser.DescriptorImpl descriptor = ((Browser.DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(Browser.class));
        if (node.toComputer().isOnline() && (!node.toComputer().isConnecting())&& (!node.toComputer().isConnecting()) && (node.toComputer().getConnectTime() > nodeActualization.get(node.getDisplayName()))) {
            //actualization of browsers label in new threads and do not wait for results. Waiting for results could cause performance problems
            doBrowserActualization(node);
        }
        return descriptor.getLabelsOfNode(node);
    }

    public void setActualizationOfBrowsers() {
        for (Node node : Hudson.getInstance().getNodes()) {
            nodeActualization.put(node.getDisplayName(), 0l);
        }
    }

    public void deleteUnusedNodes() {
        Iterator<Entry<String, Long>> it = nodeActualization.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Long> st = it.next();
            if (Hudson.getInstance().getNode(st.getKey()) == null && (!Hudson.getInstance().getDisplayName().equals(st.getKey()))) {
                it.remove(); // delete unused nodes
            }
        }
    }
}
