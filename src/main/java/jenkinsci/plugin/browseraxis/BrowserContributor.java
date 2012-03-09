/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkinsci.plugin.browseraxis;

import hudson.EnvVars;
import hudson.Extension;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixRun;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Map;

/**
 * Contribute browser build environments
 * Set BROWSER_AXIS_PATH which contains path to browser, BROWSER_AXIS which contains name of version, and export path to the version into PATH
 * 
 * @author Lucie Votypkova
 */
@Extension
public class BrowserContributor extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(Run r, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
        if (r instanceof MatrixRun)
            setEnvironment((MatrixRun)r, envs,listener);
    }

    public void setEnvironment(MatrixRun run, EnvVars envs, TaskListener listener) {
        AxisList axes = run.getParent().getParent().getAxes();
        for (Map.Entry<String, String> e : run.getParent().getCombination().entrySet()) {
            Axis a = axes.find(e.getKey());
            if (a != null && (a instanceof BrowserVariableSetter))
                ((BrowserVariableSetter) a).setEnviromentVariables(e.getValue(), envs, listener.getLogger(), run.getBuiltOn());
        }
    }
}
