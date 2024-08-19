package com.algorithmfusion.apps.sim.anu.tuidoor;

import static com.algorithmfusion.anu.sm.observers.impl.ObserversFactory.createTextStateObserver;
import static com.algorithmfusion.anu.sm.observers.impl.ObserversFactory.createTextTransitionObserver;
import static com.algorithmfusion.anu.sm.triggers.TriggersFactory.createTimerTrigger;

import com.algorithmfusion.anu.flow.Flow;
import com.algorithmfusion.anu.flow.FlowObserver;
import com.algorithmfusion.anu.flow.FlowObserversRegistry;
import com.algorithmfusion.anu.sm.api.State;
import com.algorithmfusion.anu.sm.api.Transition;
import com.algorithmfusion.anu.sm.base.BaseState;
import com.algorithmfusion.anu.sm.base.BaseTransition;
import com.algorithmfusion.anu.sm.triggers.TimerTrigger;
import com.algorithmfusion.anu.storage.api.ObjectStorage;
import com.algorithmfusion.anu.storage.impl.InMemoryObjectStorage;
import com.algorithmfusion.apps.sim.anu.tui.FlowTui;

public class MainTuiApplication {

	public static void main(String[] args) {
		createStateMachine(new InMemoryObjectStorage());
	}

	private static void createStateMachine(ObjectStorage objectStorage) {
		
		State close = new BaseState();
		State opening = new BaseState();
		State open = new BaseState();
		State closing = new BaseState();
		State stayOpen = new BaseState();
///*1 S */	State locked = new BaseState();//add new state
		

		Transition openOnClose = BaseTransition.builder().from(close).to(opening).build();
///*1 T */Transition timeoutOnClose = BaseTransition.builder().from(close).to(opening).build();//add new transition
		Transition closeOnOpening = BaseTransition.builder().from(opening).to(closing).build();
		Transition openOnClosing = BaseTransition.builder().from(closing).to(opening).build();
		Transition completeOpening = BaseTransition.builder().from(opening).to(open).build();
		Transition timeoutOnOpen = BaseTransition.builder().from(open).to(closing).build();
		Transition completeClosing = BaseTransition.builder().from(closing).to(close).build();
		Transition stayopenOnOpen = BaseTransition.builder().from(open).to(stayOpen).build();
		Transition closeOnStayOpen = BaseTransition.builder().from(stayOpen).to(closing).build();
		
///*2 S */  Transition lockOnStayOpen = BaseTransition.builder().from(stayOpen).to(locked).build();//add new state

		FlowTui tui = new FlowTui();

		FlowObserver flowObserver = new FlowObserver();
		
		Flow flow = Flow.builder()
					.stateMachineObserver(flowObserver)
				.build();
		
		FlowObserversRegistry flowObserversRegistry = FlowObserversRegistry.builder()
		
		.addPerformTransitionObserver(openOnClose, createTextTransitionObserver("Perform transition(openOnClose)"))
		
///*2 T */.addPerformTransitionObserver(timeoutOnClose, createTextTransitionObserver("Perform transition(timeoutOnClose)"))//add new transition
		
		.addPerformTransitionObserver(closeOnOpening, createTextTransitionObserver("Perform transition(closeOnOpening)"))
		.addPerformTransitionObserver(openOnClosing, createTextTransitionObserver("Perform transition(openOnClosing)"))
		.addPerformTransitionObserver(completeOpening, createTextTransitionObserver("Perform transition(completeOpening)"))
		.addPerformTransitionObserver(timeoutOnOpen, createTextTransitionObserver("Perform transition(timeoutOnOpen)"))
		.addPerformTransitionObserver(completeClosing, createTextTransitionObserver("Perform transition(completeClosing)"))
		.addPerformTransitionObserver(stayopenOnOpen, createTextTransitionObserver("Perform transition(stayopenOnOpen)"))
		.addPerformTransitionObserver(closeOnStayOpen, createTextTransitionObserver("Perform transition(closeOnStayOpen)"))

///*3 S */.addPerformTransitionObserver(lockOnStayOpen, createTextTransitionObserver("Perform transition(lockOnStayOpen)"))//add new state
		
///*3 T */.addPrepareTransitionObserver(timeoutOnClose, objectStorage.store("timeoutOnClose", createTimerTrigger(flow, timeoutOnClose, 10000, 10, "timeoutOnClose")).getPrepare())//add new transition
///*4 T */.addDisposeTransitionObserver(timeoutOnClose, objectStorage.retrieve("timeoutOnClose", TimerTrigger.class).getDispose())//add new transition
		
		.addPrepareTransitionObserver(timeoutOnOpen, objectStorage.store("timeoutOnOpen", createTimerTrigger(flow, timeoutOnOpen, 10000, 10, "timeoutOnOpen")).getPrepare())
		.addDisposeTransitionObserver(timeoutOnOpen, objectStorage.retrieve("timeoutOnOpen", TimerTrigger.class).getDispose())
		
		.addLeaveStateObservers(close, createTextStateObserver("Leave State(close)"))
		.addLeaveStateObservers(opening, createTextStateObserver("Leave State(opening)"))
		.addLeaveStateObservers(open, createTextStateObserver("Leave State(open)"))
		.addLeaveStateObservers(closing, createTextStateObserver("Leave State(closing)"))
		.addLeaveStateObservers(stayOpen, createTextStateObserver("Leave State(stayOpen)"))
		
		.addEnterStateObservers(close, createTextStateObserver("Enter State(close)"), tui)
		.addEnterStateObservers(opening, createTextStateObserver("Enter State(opening)"), tui)
		.addEnterStateObservers(open, createTextStateObserver("Enter State(open)"), tui)
		.addEnterStateObservers(closing, createTextStateObserver("Enter State(closing)"), tui)
		.addEnterStateObservers(stayOpen, createTextStateObserver("Enter State(stayOpen)"), tui)
		
///*4 S */.addEnterStateObservers(locked, createTextStateObserver("Enter State(locked)"), tui)//add new state
		.build();

		flowObserver.setFlowObserversRegistry(flowObserversRegistry);

		tui.setFlow(flow, flowObserversRegistry);
		
		flow.startingState(close);
	}
}
