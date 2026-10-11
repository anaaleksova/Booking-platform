package com.bookingsaas.integration.conversation;

/** The small vocabularies the conversation API speaks. */
public final class ConversationEnums {

    private ConversationEnums() {
    }

    public enum Flow {
        BOOKING,
        CANCELLATION
    }

    public enum State {
        WAITING_FOR_SERVICE,
        WAITING_FOR_PARTICIPANTS,
        WAITING_FOR_DATE,
        WAITING_FOR_TIME,
        WAITING_FOR_RESOURCE,
        WAITING_FOR_CUSTOMER_INFO,
        WAITING_FOR_CONFIRMATION,
        /** Confirmed by the customer; the workflow is creating the reservation. */
        CREATING_RESERVATION,
        WAITING_FOR_CANCEL_SELECTION,
        WAITING_FOR_CANCEL_CONFIRMATION,
        /** Confirmed by the customer; the workflow is cancelling the reservation. */
        CANCELLING,
        COMPLETED,
        /** Abandoned by the customer, or expired. */
        CANCELLED;

        public boolean isFinal() {
            return this == COMPLETED || this == CANCELLED;
        }
    }

    /** What the customer wants, as the language model understood the message. */
    public enum Intent {
        BOOK,
        CANCEL,
        RESCHEDULE,
        QUESTION,
        GREETING,
        UNKNOWN
    }

    public enum Confirmation {
        YES,
        NO
    }

    public enum TimeOfDay {
        MORNING,
        AFTERNOON,
        EVENING
    }

    /** What the reply should ask the customer for. */
    public enum Ask {
        SERVICE,
        PARTICIPANTS,
        DATE,
        TIME,
        RESOURCE,
        CUSTOMER_INFO,
        CONFIRMATION,
        CANCEL_SELECTION,
        CANCEL_CONFIRMATION,
        NOTHING
    }

    /** What the workflow must do before replying. */
    public enum NextAction {
        REPLY,
        CREATE_RESERVATION,
        CANCEL_RESERVATION
    }

    /** How a conversation ended, when it did. */
    public enum Outcome {
        RESERVATION_CREATED,
        RESERVATION_CANCELLED,
        BOOKING_ABANDONED,
        CANCELLATION_ABORTED,
        NO_UPCOMING_RESERVATIONS,
        CANCELLATION_NOT_ALLOWED,
        CANCELLATION_FAILED
    }
}
