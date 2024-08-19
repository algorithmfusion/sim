package com.algorithmfusion.apps.sim.anu.tuitenetprocess;

import static com.algorithmfusion.anu.sm.observers.impl.ObserversFactory.createTextStateObserver;
import static com.algorithmfusion.anu.sm.observers.impl.ObserversFactory.createTextTransitionObserver;
import static com.algorithmfusion.anu.sm.triggers.TriggersFactory.createTimerTrigger;

import java.util.Arrays;
import java.util.Random;

import com.algorithmfusion.anu.flow.Flow;
import com.algorithmfusion.anu.flow.FlowObserver;
import com.algorithmfusion.anu.flow.FlowObserversRegistry;
import com.algorithmfusion.anu.sm.api.State;
import com.algorithmfusion.anu.sm.api.Transition;
import com.algorithmfusion.anu.sm.base.BaseState;
import com.algorithmfusion.anu.sm.base.BaseTransition;
import com.algorithmfusion.anu.sm.observers.impl.ConditionalTransitionObserver;
import com.algorithmfusion.anu.sm.triggers.TimerTrigger;
import com.algorithmfusion.anu.sm.triggers.TrigableStateMachineTransition;
import com.algorithmfusion.anu.storage.api.ObjectStorage;
import com.algorithmfusion.anu.storage.impl.InMemoryObjectStorage;
import com.algorithmfusion.anu.storage.observers.StoreKeyValueOnStateObserver;
import com.algorithmfusion.anu.storage.observers.StoreKeyValueOnTransitionObserver;
import com.algorithmfusion.anu.storage.predicates.IsKeyStored;
import com.algorithmfusion.anu.storage.triggers.OnKeyValueAvailabilityTransitionObserverTrigger;
import com.algorithmfusion.anu.storage.triggers.OnKeysAvailabilityStateObserverTrigger;
import com.algorithmfusion.apps.sim.anu.tui.FlowTui;

public class MainTuiCreateSps {
	private static Random random = new Random();
	
	public static void main(String[] args) {
		createStateMachine(new InMemoryObjectStorage());
	}

	private static void createStateMachine(ObjectStorage objectStorage) {
		
		State collectingInputForSpsCreation = new BaseState();
		State creatingSpsAutomatically = new BaseState();
		State creatingSpsManually = new BaseState();
		State spsCreated = new BaseState();
		State sendingSps = new BaseState();
		State spsSend = new BaseState();
		State confirmingSpsAck = new BaseState();
		State spsAckReceived = new BaseState();
		State spsCreationDone = new BaseState();

		Transition cbsReceived = BaseTransition.builder().from(collectingInputForSpsCreation).to(collectingInputForSpsCreation).build();
		Transition opcReceived = BaseTransition.builder().from(collectingInputForSpsCreation).to(collectingInputForSpsCreation).build();
		Transition requiredInputCollected = BaseTransition.builder().from(collectingInputForSpsCreation).to(creatingSpsAutomatically).build();
		Transition createSpsManually = BaseTransition.builder().from(collectingInputForSpsCreation).to(creatingSpsManually).build();
		Transition doneAutomaticCreation = BaseTransition.builder().from(creatingSpsAutomatically).to(spsCreated).build();
		Transition doneManualCreation = BaseTransition.builder().from(creatingSpsManually).to(spsCreated).build();
		Transition send = BaseTransition.builder().from(spsCreated).to(sendingSps).build();
		Transition retryOnfail = BaseTransition.builder().from(sendingSps).to(sendingSps).build();
		Transition succeed = BaseTransition.builder().from(sendingSps).to(spsSend).build();
		Transition confirm = BaseTransition.builder().from(spsSend).to(confirmingSpsAck).build();
		Transition received = BaseTransition.builder().from(confirmingSpsAck).to(spsAckReceived).build();
		Transition notOk = BaseTransition.builder().from(spsAckReceived).to(spsCreated).build();
		Transition ok = BaseTransition.builder().from(spsAckReceived).to(spsCreationDone).build();

		FlowTui tui = new FlowTui();

		FlowObserver flowObserver = new FlowObserver();
		
		Flow flow = Flow.builder()
							.stateMachineObserver(flowObserver)
						.build();
		
		String cbsReceivedTimerId = "cbsReceivedTimerId";
		int cbsInterval = random(5, 15) * 1000;
		int cbsTicks = cbsInterval/1000;
		String opcReceivedTimerId = "opcReceivedTimerId";
		int opcInterval = random(5, 15) * 1000;
		int opcTicks = opcInterval/1000;
		
		FlowObserversRegistry flowObserversRegistry = FlowObserversRegistry.builder()
		
		.addPerformTransitionObserver(cbsReceived,
			createTextTransitionObserver("Perform transition(cbsReceived)"),
			new StoreKeyValueOnTransitionObserver<Transition, String>(objectStorage, cbsReceived, "CBS"))
		
		.addPerformTransitionObserver(opcReceived,
			createTextTransitionObserver("Perform transition(opcReceived)"),
			new StoreKeyValueOnTransitionObserver<Transition, String>(objectStorage, opcReceived, "OPC"))
		
		.addPerformTransitionObserver(requiredInputCollected, createTextTransitionObserver("Perform transition(requiredInputCollected)"))
		.addPerformTransitionObserver(createSpsManually, createTextTransitionObserver("Perform transition(createSpsManually)"))
		.addPerformTransitionObserver(doneAutomaticCreation, createTextTransitionObserver("Perform transition(doneAutomaticCreation)"))
		.addPerformTransitionObserver(doneManualCreation, createTextTransitionObserver("Perform transition(doneManualCreation)"))
		.addPerformTransitionObserver(send, createTextTransitionObserver("Perform transition(send)"))
		.addPerformTransitionObserver(retryOnfail, createTextTransitionObserver("Perform transition(retryOnfail)"))
		.addPerformTransitionObserver(succeed, createTextTransitionObserver("Perform transition(succeed)"))
		.addPerformTransitionObserver(confirm, createTextTransitionObserver("Perform transition(confirm)"))
		.addPerformTransitionObserver(received, createTextTransitionObserver("Perform transition(received)"))
		.addPerformTransitionObserver(notOk, createTextTransitionObserver("Perform transition(notOk)"))
		.addPerformTransitionObserver(ok, createTextTransitionObserver("Perform transition(ok)"))

		.addPrepareTransitionObserver(cbsReceived, 
			new ConditionalTransitionObserver(
				objectStorage.store(cbsReceivedTimerId, createTimerTrigger(flow, cbsReceived, cbsInterval, cbsTicks, cbsReceivedTimerId)).getPrepare(),
				new IsKeyStored<Transition>(objectStorage).negate()))
		.addDisposeTransitionObserver(cbsReceived,
			new ConditionalTransitionObserver(
				objectStorage.retrieve(cbsReceivedTimerId, TimerTrigger.class).getDispose(),
				new IsKeyStored<Transition>(objectStorage).negate()))
		
		.addPrepareTransitionObserver(opcReceived,
			new ConditionalTransitionObserver(
				objectStorage.store(opcReceivedTimerId, createTimerTrigger(flow, opcReceived, opcInterval, opcTicks, opcReceivedTimerId)).getPrepare(),
				new IsKeyStored<Transition>(objectStorage).negate()))
		.addDisposeTransitionObserver(opcReceived,
			new ConditionalTransitionObserver(
				objectStorage.retrieve(opcReceivedTimerId, TimerTrigger.class).getDispose(),
				new IsKeyStored<Transition>(objectStorage).negate()))
		
		.addPrepareTransitionObserver(doneAutomaticCreation,
			new OnKeyValueAvailabilityTransitionObserverTrigger<State, String>(
				new TrigableStateMachineTransition(flow, doneAutomaticCreation), objectStorage, creatingSpsAutomatically, "SPS"))
				
		.addLeaveStateObservers(collectingInputForSpsCreation, createTextStateObserver("Leave State(collectingInputForSpsCreation)"))
		.addLeaveStateObservers(creatingSpsAutomatically, createTextStateObserver("Leave State(creatingSpsAutomatically)"))
		.addLeaveStateObservers(creatingSpsManually, createTextStateObserver("Leave State(creatingSpsManually)"))
		.addLeaveStateObservers(spsCreated, createTextStateObserver("Leave State(spsCreated)"))
		.addLeaveStateObservers(sendingSps, createTextStateObserver("Leave State(sendingSps)"))
		.addLeaveStateObservers(spsSend, createTextStateObserver("Leave State(spsSend)"))
		.addLeaveStateObservers(confirmingSpsAck, createTextStateObserver("Leave State(confirmingSpsAck)"))
		.addLeaveStateObservers(spsAckReceived, createTextStateObserver("Leave State(spsAckReceived)"))
		.addLeaveStateObservers(spsCreationDone, createTextStateObserver("Leave State(spsCreationDone)"))
		
		.addEnterStateObservers(collectingInputForSpsCreation,
			createTextStateObserver("Enter State(collectingInputForSpsCreation)"), tui,
			new OnKeysAvailabilityStateObserverTrigger(
				new TrigableStateMachineTransition(flow, requiredInputCollected), objectStorage, Arrays.asList(cbsReceived, opcReceived)))
		
		.addEnterStateObservers(creatingSpsAutomatically,
			createTextStateObserver("Enter State(creatingSpsAutomatically)"), tui,
			new StoreKeyValueOnStateObserver<State, String>(objectStorage, creatingSpsAutomatically, "SPS"))
		
		.addEnterStateObservers(creatingSpsManually, createTextStateObserver("Enter State(creatingSpsManually)"), tui)
		.addEnterStateObservers(spsCreated, createTextStateObserver("Enter State(spsCreated)"), tui)
		.addEnterStateObservers(sendingSps, createTextStateObserver("Enter State(sendingSps)"), tui)
		.addEnterStateObservers(spsSend, createTextStateObserver("Enter State(spsSend)"), tui)
		.addEnterStateObservers(confirmingSpsAck, createTextStateObserver("Enter State(confirmingSpsAck)"), tui)
		.addEnterStateObservers(spsAckReceived, createTextStateObserver("Enter State(spsAckReceived)"), tui)
		.addEnterStateObservers(spsCreationDone, createTextStateObserver("Enter State(spsCreationDone)"), tui)
		.build();

		flowObserver.setFlowObserversRegistry(flowObserversRegistry);

		tui.setFlow(flow, flowObserversRegistry);
		
		flow.startingState(collectingInputForSpsCreation);
	}

	private static int random(int lowerBound, int upperBound) {
		return random.nextInt(upperBound - lowerBound) + lowerBound;
	}
}