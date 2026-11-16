package io.temporal.demo.wealthmanagement;

import io.temporal.client.WorkflowClient;
import io.temporal.demo.wealthmanagement.activities.UpdateParentWorkflowImpl;
import io.temporal.demo.wealthmanagement.common.ServerInfo;
import io.temporal.demo.wealthmanagement.workflow.OpenInvestmentAccountWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runs OpenInvestmentAccountWorkflow on the open-account task queue
 * (TEMPORAL_TASK_QUEUE_OPEN_ACCOUNT). Its business activities are the Python ones, called by
 * name on the Python worker's task queue, so the Python worker must run too.
 */
@SpringBootApplication
public class StartTheWorker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartTheWorker.class);

    public static void main(String[] args) {
        SpringApplication.run(StartTheWorker.class, args);
    }

    @Autowired
    private WorkflowClient client;

    @Override
    public void run(String... args) {
        // Workers aren't auto-discovered (no spring.temporal.workers in application.yaml),
        // so the worker is created here.
        WorkerFactory workerFactory = WorkerFactory.newInstance(client);
        Worker worker = workerFactory.newWorker(ServerInfo.getTaskqueueOpenAccount());
        worker.registerWorkflowImplementationTypes(OpenInvestmentAccountWorkflowImpl.class);
        worker.registerActivitiesImplementations(new UpdateParentWorkflowImpl());
        workerFactory.start();
        log.info("Open-account worker started: {}", ServerInfo.getServerInfo());
        log.info(ServerInfo.claimCheckStatus());
    }
}
