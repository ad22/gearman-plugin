/*
 *
 * Copyright 2013 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package hudson.plugins.gearman;

import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.model.Node;
import hudson.model.Node.Mode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import jenkins.model.Jenkins;

import org.gearman.worker.GearmanFunctionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
 * This is the thread to run gearman executors
 * Executors are used to initiate jenkins builds
 *
 * @author Khai Do
 *
 */
public class ExecutorWorkerThread extends AbstractWorkerThread{

    private static final Logger logger = LoggerFactory
            .getLogger(Constants.PLUGIN_LOGGER_NAME);

    private final Computer computer;
    private final String masterName;

    private HashMap<String,GearmanFunctionFactory> functionMap;

    // constructor
    public ExecutorWorkerThread(String host, int port, String name,
                                Computer computer, String masterName,
                                AvailabilityMonitor availability) {
        super(host, port, name, availability);
        this.computer = computer;
        this.masterName = masterName;
    }

    @Override
    protected void initWorker() {
        availability.unlock(worker);
        super.initWorker();
        this.functionMap = new HashMap<String,GearmanFunctionFactory>();
    }

    /**
     * Register gearman functions on this computer.  This will unregister all
     * functions before registering new functions.  Works for free-style
     * and maven projects but does not work for multi-config projects
     *
     * How functions are registered:
     *  - If the project has no label then we register the project with all
     *      computers
     *
     *      build:pep8 on precise-123
     *      build:pep8 on oneiric-456
     *
     *  - If the project contains one label then we register with the computer
     *      that contains the corresponding label. Labels with '&&' is
     *      considered just one label
     *
     *      build:pep8:precise on precise-123
     *      build:pep8 on precise-123
     *      build:pep8:precise on precise-129
     *      build:pep8 on precise-129
     *
     *  - If the project contains multiple labels separated by '||' then
     *      we register with the computers that contain the corresponding labels
     *
     *      build:pep8:precise on precise-123
     *      build:pep8 on precise-123
     *      build:pep8:precise on precise-129
     *      build:pep8 on precise-129
     *      build:pep8:oneiric on oneiric-456
     *      build:pep8 on oneiric-456
     *      build:pep8:oneiric on oneiric-459
     *      build:pep8 on oneiric-459
     *
     */
    @Override
    public void registerJobs() {
        if (worker == null || functionMap == null) {
            // We haven't been initialized yet; the run method will call this again
            return;
        }

        HashMap<String, GearmanFunctionFactory> newFunctionMap = new HashMap<String, GearmanFunctionFactory>();

        if (!computer.isOffline()) {
            List<AbstractProject> allProjects = Jenkins.getActiveInstance().getAllItems(AbstractProject.class);
            for (AbstractProject<?, ?> project : allProjects) {

                if (project.isDisabled()) { // ignore all disabled projects
                    continue;
                }

                String projectName = project.getName();
                String jobFunctionName = "build:" + projectName
                        + ":" + "scheduler";

                newFunctionMap.put(jobFunctionName, new CustomGearmanFunctionFactory(
                        jobFunctionName, StartJobWorker.class.getName(),
                        project, computer, this.masterName, worker));
            }
        }
        if (!newFunctionMap.keySet().equals(functionMap.keySet())) {
            functionMap = newFunctionMap;
            Set<GearmanFunctionFactory> functionSet = new HashSet<GearmanFunctionFactory>(functionMap.values());
            updateJobs(functionSet);
        }
    }

    public synchronized Computer getComputer() {
        return computer;
    }
}
