from dataclasses import dataclass
from typing import Optional


@dataclass
class UpdateAccountOpeningStateInput:
    """Payload of the update_account_opening_state signal, sent by the open-account child."""
    account_name: str
    state: str
    # The open-account workflow that reported the state. Optional so that
    # senders that don't set it keep working.
    workflow_id: Optional[str] = None
