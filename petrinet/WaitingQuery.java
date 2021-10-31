package petrinet;

import java.util.Collection;
import java.util.concurrent.Semaphore;

class WaitingQuery<T> {
    public enum STATE {
        WAITING,
        INTERRUPTED,
        UNINTERRUPTIBLE
    }

    Collection<Transition<T>> transitions;

    // Mutex for waiting until transitions become enabled
    Semaphore mutex = new Semaphore(0);

    // Mutex for internal state protection
    private Semaphore stateMutex = new Semaphore(1);

    private STATE state = STATE.WAITING;

    WaitingQuery(Collection<Transition<T>> transitions) {
        this.transitions = transitions;
    }

    // Attempts to interrupt the query. Returns false when it's too late, and PetriNet already tried to resume this thread.
    boolean interrupt() {
        boolean result = false;

        stateMutex.acquireUninterruptibly();

        if(state == STATE.WAITING) {
            state = STATE.INTERRUPTED;
            result = true;
        }

        stateMutex.release();
        return result;
    }

    // Attempts to resume the query. Returns false when it's been interrupted before, and we can still cancel fireing.
    boolean resume() {
        boolean result = false;

        stateMutex.acquireUninterruptibly();

        if(state == STATE.WAITING) {
            state = STATE.UNINTERRUPTIBLE;
            result = true;
        }

        mutex.release();
        stateMutex.release();
        return result;
    }

}
