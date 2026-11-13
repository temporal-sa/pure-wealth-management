package io.temporal.demo.wealthmanagement.activities;

import io.temporal.client.WorkflowStub;
import io.temporal.demo.wealthmanagement.common.ServerInfo;
import io.temporal.demo.wealthmanagement.common.TemporalClient;
import io.temporal.demo.wealthmanagement.data.UpdateAccountOpeningStateInput;

public class UpdateParentWorkflowImpl implements UpdateParentWorkflow {

    @Override
    public void changeState(String parentWorkflowId, String accountName, String state, String workflowId) {
        try {
            WorkflowStub parent = TemporalClient.get().newUntypedWorkflowStub(parentWorkflowId);
            // The same signal and payload as the Python open-account workflow sends
            parent.signal("update_account_opening_state",
                    new UpdateAccountOpeningStateInput(accountName, state, workflowId));
        } catch (Exception ex) {
            String message = String.format("Exception while notifying parent. ParentWFID %s, State %s, ServerInfo %s",
                    parentWorkflowId, state, ServerInfo.getServerInfo());
            throw new RuntimeException(message, ex);
        }
    }
}
