/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkinsci.plugin.browseraxis;

import hudson.EnvVars;
import hudson.model.Node;
import java.io.PrintStream;

/**
 *
 * @author LucieVotypkova
 */
public interface BrowserVariableSetter {
    
    public void setEnviromentVariables(String value, EnvVars envVar, PrintStream log, Node node);
    
}
