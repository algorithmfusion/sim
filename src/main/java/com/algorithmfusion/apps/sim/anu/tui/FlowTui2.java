package com.algorithmfusion.apps.sim.anu.tui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.algorithmfusion.anu.sm.api.State;
import com.algorithmfusion.anu.sm.api.StateMachine;
import com.algorithmfusion.anu.sm.api.Transition;
import com.algorithmfusion.anu.sm.observers.StateObserver;

public class FlowTui2 implements StateObserver, Runnable {

	private StateMachine flow;
	private Map<String, Transition> selectionToTransition;

	public FlowTui2() {
		new Thread(this).start();
	}
	
	
	public void setFlow(StateMachine flow) {
		this.flow = flow;
	}

	@Override
	public void notify(State state) {
		this.selectionToTransition = showTransitions(state);
		
	}

	private static Map<String, Transition> showTransitions(State state) {
		Map<String, Transition> selectionToTransition = new HashMap<>();
		int selection = 0;
		System.out.println("\n" + (state.getOutgoingTransitions().isEmpty() ? "No transitions available!" :"Select transition:"));
		for (Transition transition : state.getOutgoingTransitions()) {
			System.out.println(++selection + ") "
					+ getSelectionTextFromTransition(transition));
			selectionToTransition.put(String.valueOf(selection), transition);
		}
		return selectionToTransition;
	}

	private static String getSelectionTextFromTransition(Transition transition) {
		return ((com.algorithmfusion.anu.flow.BpmnTransition) transition).getName();
	}

	private Transition selectTransition() throws IOException {
		Transition transition = null;
		InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);
		String selection = br.readLine();

		if ("exit".equalsIgnoreCase(selection)) {
			System.out.print("Terminating State machine application !");
			System.exit(0);
		} else {
			transition = selectionToTransition.get(selection);
			if (transition == null) {
				System.out.print("Make sure you select a valid transition !");
			}
		}
		System.out.println();
		return transition;
	}

	@Override
	public void run() {
		Transition transition;
		while (true) {
			try {
				transition = selectTransition();
				if (transition != null) {
					flow.transition(transition);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
