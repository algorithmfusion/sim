package com.algorithmfusion.apps.sim.anu.tuidoor;

import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.DISPOSE_TRANSITION;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.ENTER_STATE;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.LEAVE_STATE;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.PERFORM_TRANSITION;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.PREPARE_TRANSITION;
import static com.algorithmfusion.anu.sm.observers.ObserversFactory.createTextStateObserver;
import static com.algorithmfusion.anu.sm.observers.ObserversFactory.createTextTransitionObserver;
import static com.algorithmfusion.anu.sm.triggers.TriggersFactory.createTimerTrigger;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.schema.zeebe._1.Properties.Property;
import org.omg.spec.bpmn._20100524.model.Definitions;
import org.omg.spec.bpmn._20100524.model.Definitions.Process.SequenceFlow;
import org.omg.spec.bpmn._20100524.model.Definitions.Process.StartEvent;
import org.omg.spec.bpmn._20100524.model.Definitions.Process.Task;

import com.algorithmfusion.anu.flow.BpmnFlow;
import com.algorithmfusion.anu.flow.FlowObservableLifeCycle;
import com.algorithmfusion.anu.flow.FlowObserver;
import com.algorithmfusion.anu.flow.FlowObserversRegistry;
import com.algorithmfusion.anu.flow.FlowObserversRegistry.Builder;
import com.algorithmfusion.anu.sm.api.State;
import com.algorithmfusion.anu.sm.api.Transition;
import com.algorithmfusion.anu.sm.base.BaseState;
import com.algorithmfusion.anu.sm.base.BaseTransition;
import com.algorithmfusion.anu.sm.observers.StateObserver;
import com.algorithmfusion.anu.sm.observers.TransitionObserver;
import com.algorithmfusion.anu.sm.triggers.TimerTrigger;
import com.algorithmfusion.anu.storage.api.ObjectStorage;
import com.algorithmfusion.anu.storage.impl.InMemoryObjectStorage;
import com.algorithmfusion.apps.sim.anu.tui.FlowTui;
import com.algorithmfusion.libs.xml.parser.XmlParser;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import jakarta.xml.bind.JAXBException;

public class MainTuiApplicationWithBPMN {

	public static void main(String[] args) throws FileNotFoundException, JAXBException {
		String fileName = "C:\\rnd\\sandbox\\statemachine\\src\\main\\resources\\mydiagram.bpmn";
		Definitions definitions = XmlParser.parse(fileName, Definitions.class);
		System.out.println("Loading BPMN process (" + definitions.getProcess().getName() + ")\n");
		createStateMachine(definitions, new InMemoryObjectStorage());
	}

	private static Map<String, Object> context = new HashMap<>();
	
	private static void createStateMachine(Definitions definitions, ObjectStorage objectStorage) {
		List<Object> processElements = definitions.getProcess().getExtensionElementsOrStartEventOrTask();
		
		StartEvent startEvent = extractStartEvent(processElements);
		SequenceFlow startSequenceFlow = extractStartSequenceFlows(startEvent, processElements);
		Map<String, Task> idToTask = extractTasks(processElements);
		Map<String, SequenceFlow> idToSequenceFlow = extractSequenceFlows(startEvent, processElements);
		Multimap<String, String> taskIdToEnterStateExtensionElementPropertieValues = extractTaskExtensionElementPropertieValues(toExtensionId(ENTER_STATE), idToTask.values());
		Multimap<String, String> taskIdToLeaveStateExtensionElementPropertieValues = extractTaskExtensionElementPropertieValues(toExtensionId(LEAVE_STATE), idToTask.values());
		
		Multimap<String, String> sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues = extractSequenceFlowExtensionElementPropertieValues(toExtensionId(PREPARE_TRANSITION), idToSequenceFlow.values());
		Multimap<String, String> sequenceFlowIdToPerformTransitionExtensionElementPropertieValues = extractSequenceFlowExtensionElementPropertieValues(toExtensionId(PERFORM_TRANSITION), idToSequenceFlow.values());
		Multimap<String, String> sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues = extractSequenceFlowExtensionElementPropertieValues(toExtensionId(DISPOSE_TRANSITION), idToSequenceFlow.values());
		
		Map<String, State> idToState = createStates(idToTask.values());
		Map<String, Transition> idToTransition = createTransitions(idToState, idToSequenceFlow.values());

		FlowTui tui = new FlowTui();
		
		context.put("tui", tui);
		
		FlowObserver flowObserver = new FlowObserver();
		
		BpmnFlow flow = BpmnFlow.builder()
							.id(definitions.getProcess().getId())
							.name(definitions.getProcess().getName())
							.stateMachineObserver(flowObserver)
						.build();
		
		FlowObserversRegistry.Builder builder = FlowObserversRegistry.builder();
		
		addEnterStateObservers(builder, idToState, taskIdToEnterStateExtensionElementPropertieValues);
		addLeaveStateObservers(builder, idToState, taskIdToLeaveStateExtensionElementPropertieValues);
		
		addPrepareTransitionObservers(builder, idToTransition, sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues, flow, objectStorage);
		addPerformTransitionObservers(builder, idToTransition, sequenceFlowIdToPerformTransitionExtensionElementPropertieValues, flow, objectStorage);
		addDisposeTransitionObservers(builder, idToTransition, sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues, flow, objectStorage);
		
		FlowObserversRegistry flowObserversRegistry = builder.build();

		flowObserver.setFlowObserversRegistry(flowObserversRegistry);

		tui.setFlow(flow, flowObserversRegistry);
		
		State startingState = idToState.get(startSequenceFlow.getTargetRef());
		
		flow.startingState(startingState);
	}

	////////////////////// Observers creation
	private static void addEnterStateObservers(
			Builder builder,
			Map<String, State> idToState,
			Multimap<String, String> taskIdToEnterStateExtensionElementPropertieValues) {
		taskIdToEnterStateExtensionElementPropertieValues.keySet().forEach(taskId -> {
			StateObserver[] stateObservers = taskIdToEnterStateExtensionElementPropertieValues.get(taskId)
					.stream().map(value -> textToStateObserver(value)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new StateObserver[] {});
			builder.addEnterStateObservers(idToState.get(taskId), stateObservers);
		});
	}

	private static void addLeaveStateObservers(
			Builder builder,
			Map<String, State> idToState,
			Multimap<String, String> taskIdToLeaveStateExtensionElementPropertieValues) {
		taskIdToLeaveStateExtensionElementPropertieValues.keySet().forEach(taskId -> {
			StateObserver[] stateObservers = taskIdToLeaveStateExtensionElementPropertieValues.get(taskId)
					.stream().map(value -> textToStateObserver(value)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new StateObserver[] {});
			builder.addLeaveStateObservers(idToState.get(taskId), stateObservers);
		});
	}
	
	private static StateObserver textToStateObserver(String text) {
		String trimedText = text.trim();
		String extensionValueType = extractExtensionValueType(trimedText);
		String[] parameters = extractExtensionValueTypeParameters(trimedText);
		switch (extensionValueType) {
			case "TextStateObserver":
				return createTextStateObserver(parameters[0]);
			case "Context":
				return createContextStateObserver(parameters[0]);
			default:
				return null;
		}
	}

	private static StateObserver createContextStateObserver(String stateObserverParameter) {
		return (StateObserver) context.get(stateObserverParameter);
	}

	private static String extractExtensionValueType(String trimedText) {
		return trimedText.substring(0, trimedText.indexOf('('));
	}

	private static String[] extractExtensionValueTypeParameters(String trimedText) {
		String extensionValueTypeParameters = trimedText.substring(trimedText.indexOf('(') + 1, trimedText.lastIndexOf(')'));
		return Stream.of(extensionValueTypeParameters.split(","))
				.map(value -> value.trim().substring(value.trim().indexOf('"') + 1, value.trim().lastIndexOf('"')))
				.collect(Collectors.toList()).toArray(new String[] {});
	}

	private static void addPrepareTransitionObservers(
			Builder builder,
			Map<String, Transition> idToTransition,
			Multimap<String, String> sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues,
			BpmnFlow flow,
			ObjectStorage objectStorage) {
		sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues.keySet().forEach(sequenceFlowId -> {
			Transition transition = idToTransition.get(sequenceFlowId);
			TransitionObserver[] transitionObservers = sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues.get(sequenceFlowId)
					.stream().map(value -> textToTransitionObserver(value, flow, transition, objectStorage)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new TransitionObserver[] {});
			builder.addPrepareTransitionObserver(transition, transitionObservers);
		});
	}

	private static void addPerformTransitionObservers(
			Builder builder,
			Map<String, Transition> idToTransition,
			Multimap<String, String> sequenceFlowIdToPerformTransitionExtensionElementPropertieValues,
			BpmnFlow flow,
			ObjectStorage objectStorage) {
		sequenceFlowIdToPerformTransitionExtensionElementPropertieValues.keySet().forEach(sequenceFlowId -> {
			Transition transition = idToTransition.get(sequenceFlowId);
			TransitionObserver[] transitionObservers = sequenceFlowIdToPerformTransitionExtensionElementPropertieValues.get(sequenceFlowId)
					.stream().map(value -> textToTransitionObserver(value, flow, transition, objectStorage)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new TransitionObserver[] {});
			builder.addPerformTransitionObserver(transition, transitionObservers);
		});
	}

	private static void addDisposeTransitionObservers(
			Builder builder,
			Map<String, Transition> idToTransition,
			Multimap<String, String> sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues,
			BpmnFlow flow,
			ObjectStorage objectStorage) {
		sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues.keySet().forEach(sequenceFlowId -> {
			Transition transition = idToTransition.get(sequenceFlowId);
			TransitionObserver[] transitionObservers = sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues.get(sequenceFlowId)
					.stream().map(value -> textToTransitionObserver(value, flow, transition, objectStorage)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new TransitionObserver[] {});
			builder.addDisposeTransitionObserver(transition, transitionObservers);
		});
	}

	private static TransitionObserver textToTransitionObserver(String text, BpmnFlow flow, Transition transition, ObjectStorage objectStorage) {
		String trimedText = text.trim();
		String extensionValueType = extractExtensionValueType(trimedText);
		String[] parameters = extractExtensionValueTypeParameters(trimedText);
		switch (extensionValueType) {
			case "TextTransitionObserver":
				return createTextTransitionObserver(parameters[0]);
			case "TimerTriggerPrepare":
				return createTimerTriggerPrepareObserver(parameters, objectStorage, flow, transition);
			case "TimerTriggerDispose":
				return createTimerTriggerDisposeObserver(parameters, objectStorage);
			default:
				return null;
		}
	}

	private static TransitionObserver createTimerTriggerPrepareObserver(String[] parameters, ObjectStorage objectStorage, BpmnFlow flow, Transition transition) {
		String timerId = parameters[0];
		int interval = Integer.parseInt(parameters[1]);
		int ticks = Integer.parseInt(parameters[2]);
		return objectStorage.store(timerId, createTimerTrigger(flow, transition, interval, ticks, timerId)).getPrepare();
	}
	
	private static TransitionObserver createTimerTriggerDisposeObserver(String[] parameters, ObjectStorage objectStorage) {
		String timerId = parameters[0];
		return objectStorage.retrieve(timerId, TimerTrigger.class).getDispose();
	}

	////////////////////// Core State Transition creation
	private static Map<String, State> createStates(Collection<Task> tasks) {
		Map<String, State> idToState = new HashMap<>();
		tasks.forEach(task -> idToState.put(
			task.getId(),
			new BaseState()));
		return idToState;
	}
	
	private static Map<String, Transition> createTransitions(Map<String, State> idToState, Collection<SequenceFlow> sequenceFlows) {
		Map<String, Transition> idToTransition = new HashMap<>();
		sequenceFlows.forEach(sequenceFlow -> idToTransition.put(
			sequenceFlow.getId(),
			BaseTransition.builder()
				.from(idToState.get(sequenceFlow.getSourceRef()))
				.to(idToState.get(sequenceFlow.getTargetRef()))
			.build())
		);
		return idToTransition;
	}

	////////////////////// bpmn processing
	private static StartEvent extractStartEvent(List<Object> processElements) {
		return processElements.stream()
				.filter(StartEvent.class::isInstance)
				.findFirst()
				.map(StartEvent.class::cast)
			.orElseThrow();
	}

	private static SequenceFlow extractStartSequenceFlows(StartEvent startEvent, List<Object> processElements) {
		Predicate<SequenceFlow> startSequenceFlowPredicat = new StartSequenceFlowPredicat(startEvent);
		return processElements.stream()
				.filter(SequenceFlow.class::isInstance)
				.map(SequenceFlow.class::cast)
				.filter(startSequenceFlowPredicat)
				.findFirst().orElseThrow();
	}

	private static Map<String, Task> extractTasks(List<Object> processElements) {
		return processElements.stream()
				.filter(Task.class::isInstance)
				.map(Task.class::cast)
				.collect(Collectors.toMap(MainTuiApplicationWithBPMN::taskKeyMapper, MainTuiApplicationWithBPMN::taskValueMapper));
	}
	
	private static String taskKeyMapper(Task task) {
		return task.getId();
	}
	
	private static Task taskValueMapper(Task task) {
		return task;
	}
	
	private static Map<String, SequenceFlow> extractSequenceFlows(StartEvent startEvent, List<Object> processElements) {
		Predicate<SequenceFlow> notStartSequenceFlowPredicat = new StartSequenceFlowPredicat(startEvent).negate();
		return processElements.stream()
				.filter(SequenceFlow.class::isInstance)
				.map(SequenceFlow.class::cast)
				.filter(notStartSequenceFlowPredicat)
				.collect(Collectors.toMap(MainTuiApplicationWithBPMN::sequenceFlowKeyMapper, MainTuiApplicationWithBPMN::sequenceFlowValueMapper));
	}
	
	private static class StartSequenceFlowPredicat implements Predicate<SequenceFlow> {

		private final StartEvent startEvent;
		
		public StartSequenceFlowPredicat(StartEvent startEvent) {
			this.startEvent = startEvent;
		}
		
		@Override
		public boolean test(SequenceFlow sequenceFlow) {
			return startEvent.getOutgoing().equals(sequenceFlow.getId());
		}
	}
	
	private static String sequenceFlowKeyMapper(SequenceFlow sequenceFlow) {
		return sequenceFlow.getId();
	}
	
	private static SequenceFlow sequenceFlowValueMapper(SequenceFlow sequenceFlow) {
		return sequenceFlow;
	}
	
	private static Multimap<String, String> extractTaskExtensionElementPropertieValues(String extensionId, Collection<Task> tasks) {
		PropertyNameMatcher propertyNameMatcher = new PropertyNameMatcher(extensionId);
		Multimap<String, String> taskIdToExtensionElementPropertieValues = ArrayListMultimap.create();
		tasks.forEach(task -> {
			List<String> matchingPropertyValues = getMatchingPropertyValues(propertyNameMatcher, task);
			if (!matchingPropertyValues.isEmpty()) {
				taskIdToExtensionElementPropertieValues.putAll(task.getId(), matchingPropertyValues);
			}
		});
		return taskIdToExtensionElementPropertieValues;
	}

	private static List<String> getMatchingPropertyValues(Predicate<Property> propertyMatcher, Task task) {
		return task.getExtensionElements().getProperties().getProperty().stream()
			.filter(propertyMatcher)
			.map(p -> p.getValue())
			.toList();
	}
	


	private static Multimap<String, String> extractSequenceFlowExtensionElementPropertieValues(String extensionId, Collection<SequenceFlow> sequenceFlows) {
		PropertyNameMatcher propertyNameMatcher = new PropertyNameMatcher(extensionId);
		Multimap<String, String> sequenceFlowIdToExtensionElementPropertieValues = ArrayListMultimap.create();
		sequenceFlows.forEach(sequenceFlow -> {
			List<String> matchingPropertyValues = getMatchingPropertyValues(propertyNameMatcher, sequenceFlow);
			if (!matchingPropertyValues.isEmpty()) {
				sequenceFlowIdToExtensionElementPropertieValues.putAll(sequenceFlow.getId(), matchingPropertyValues);
			}
		});
		return sequenceFlowIdToExtensionElementPropertieValues;
	}
	
	private static List<String> getMatchingPropertyValues(Predicate<Property> propertyMatcher, SequenceFlow sequenceFlow) {
		return sequenceFlow.getExtensionElements().getProperties().getProperty().stream()
			.filter(propertyMatcher)
			.map(p -> p.getValue())
			.toList();
	}

	private static class PropertyNameMatcher implements Predicate<Property> {

		private final String nameToMatch;
		
		public PropertyNameMatcher(String nameToMatch) {
			this.nameToMatch = nameToMatch;
		}

		@Override
		public boolean test(Property property) {
			return nameToMatch.equals(property.getName());
		}
		
	}

	private static String toExtensionId(FlowObservableLifeCycle enterState) {
		return enterState.name();
	}
}