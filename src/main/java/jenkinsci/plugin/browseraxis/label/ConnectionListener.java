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
        String threadName = "browsers for " + computer.getNode().getDisplayName();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (threadName.equals(t.getName()) && t instanceof FindBrowsersOnNode){
                return;
            }
        }
        FindBrowsersOnNode finder =  new FindBrowsersOnNode(threadName, System.currentTimeMillis(), computer.getNode());
        finder.start();
    }
}
