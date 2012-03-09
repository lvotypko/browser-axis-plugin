/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkinsci.plugin.browseraxis.label;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import java.io.IOException;

/**
 *
 * @author lucinka
 */
@Extension
public class ConnectionListener extends ComputerListener {

    @Override
    public void onOnline(Computer computer) {
        Node node = computer.getNode();
        for (FindBrowsersOnNode t : BrowserFinder.getThreads()) {
            if (node.equals(t.getNode()))
                return;
        }
        String threadName = "browsers for " + computer.getNode().getDisplayName();
        FindBrowsersOnNode finder =  new FindBrowsersOnNode(threadName, System.currentTimeMillis(), node);
        finder.start();
        BrowserFinder.getThreads().add(finder);
    }
}
