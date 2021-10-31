package multiplicator;

import petrinet.PetriNet;
import petrinet.Transition;

import java.util.*;

public class Main {

    private static final int ThreadCount = 4;

    private enum Place {
        A,
        B,
        RESULT
    }

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);

        int a = input.nextInt();
        int b = input.nextInt();

        HashMap<Place, Integer> initialState = new HashMap<>();
        initialState.put(Place.A, a);
        initialState.put(Place.B, b);

        PetriNet<Place> pnet = new PetriNet<>(initialState, true);

        var calcTransitions = Collections.singletonList(getCalcTransition(a));
        var finishingTransitions = Collections.singletonList(getFinishingTransition());

        ArrayList<Thread> threads = new ArrayList<>();
        for(int i = 0; i < ThreadCount; i++) {
            Thread t = createWorkerThread(pnet, calcTransitions);
            threads.add(t);
            t.start();
        }

        try {
            pnet.fire(finishingTransitions);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for(Thread t : threads) {
            t.interrupt();
        }

        System.out.println(pnet.getTokens(Place.RESULT));

    }

    private static Thread createWorkerThread(PetriNet<Place> pnet, Collection<Transition<Place>> transitions) {
        return new Thread(() -> {
            while(true) {
                try {
                    pnet.fire(transitions);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    private static Transition<Place> getCalcTransition(int val) {
        var input = Collections.singletonMap(Place.B, 1);
        List<Place> reset = Collections.emptyList();
        List<Place> inhibitor = Collections.emptyList();
        var output = Collections.singletonMap(Place.RESULT, val);

        return new Transition<>(input, reset, inhibitor, output);
    }

    private static Transition<Place> getFinishingTransition() {
        Map<Place, Integer> input = Collections.emptyMap();
        List<Place> reset = Collections.emptyList();
        var inhibitor = Collections.singletonList(Place.B);
        Map<Place, Integer> output = Collections.emptyMap();

        return new Transition<>(input, reset, inhibitor, output);
    }
}
