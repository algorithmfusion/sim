package com.algorithmfusion.apps.sim.anu.tuidoor;

import static com.algorithmfusion.anu.flow.FlowConfiguration.asString;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.DISPOSE_TRANSITION;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.ENTER_STATE;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.LEAVE_STATE;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.PERFORM_TRANSITION;
import static com.algorithmfusion.anu.flow.FlowObservableLifeCycle.PREPARE_TRANSITION;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.algorithmfusion.anu.flow.Flow;
import com.algorithmfusion.anu.flow.FlowState;
import com.algorithmfusion.anu.flow.FlowTransition;
import com.algorithmfusion.anu.flow.FlowConfiguration;
import com.algorithmfusion.anu.flow.FlowObservableLifeCycle;
import com.algorithmfusion.anu.flow.FlowObserver;
import com.algorithmfusion.anu.flow.FlowObserversRegistry;
import com.algorithmfusion.anu.flow.FlowObserversRegistry.Builder;
import com.algorithmfusion.anu.generic.api.ImmutableMultiValueMap;
import com.algorithmfusion.anu.generic.impl.ImmutableMultiValueMapImpl;
import com.algorithmfusion.anu.sm.api.State;
import com.algorithmfusion.anu.sm.api.Transition;
import com.algorithmfusion.anu.sm.observers.api.StateObserver;
import com.algorithmfusion.anu.sm.observers.api.TransitionObserver;
import com.algorithmfusion.anu.storage.api.ObjectStorage;
import com.algorithmfusion.anu.storage.impl.InMemoryObjectStorage;
import com.algorithmfusion.apps.sim.anu.tui.FlowTui2;
import com.algorithmfusion.libs.xml.parser.XmlParser;

import jakarta.xml.bind.JAXBException;

public class MainTuiApplicationWithBPMN2 {

	public static void main(String[] args) throws FileNotFoundException, JAXBException {
		String fileName = args[0];
		Definitions definitions = XmlParser.parse(fileName, Definitions.class);
		System.out.println("Loading BPMN process (" + definitions.getProcess().getName() + ")\n");
		Map<Object, Object> context = new HashMap<>();
		createStateMachine(definitions, new InMemoryObjectStorage(), new FlowConfiguration(), context);
	}

	private static void createStateMachine(Definitions definitions, ObjectStorage objectStorage, FlowConfiguration flowConfiguration, Map<Object, Object> context) {
		List<Object> processElements = definitions.getProcess().getExtensionElementsOrStartEventOrTask();
		
		StartEvent startEvent = extractStartEvent(processElements);
		SequenceFlow startSequenceFlow = extractStartSequenceFlows(startEvent, processElements);
		Map<String, Task> idToTask = extractTasks(processElements);
		Map<String, SequenceFlow> idToSequenceFlow = extractSequenceFlows(startEvent, processElements);
		ImmutableMultiValueMap<String, String> taskIdToEnterStateExtensionElementPropertieValues = extractTaskExtensionElementPropertieValues(asString(ENTER_STATE), idToTask.values());
		ImmutableMultiValueMap<String, String> taskIdToLeaveStateExtensionElementPropertieValues = extractTaskExtensionElementPropertieValues(asString(LEAVE_STATE), idToTask.values());
		
		ImmutableMultiValueMap<String, String> sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues = extractSequenceFlowExtensionElementPropertieValues(asString(PREPARE_TRANSITION), idToSequenceFlow.values());
		ImmutableMultiValueMap<String, String> sequenceFlowIdToPerformTransitionExtensionElementPropertieValues = extractSequenceFlowExtensionElementPropertieValues(asString(PERFORM_TRANSITION), idToSequenceFlow.values());
		ImmutableMultiValueMap<String, String> sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues = extractSequenceFlowExtensionElementPropertieValues(asString(DISPOSE_TRANSITION), idToSequenceFlow.values());
		
		Map<String, State> idToState = createStates(idToTask.values());
		Map<String, Transition> idToTransition = createTransitions(idToState, idToSequenceFlow.values());
		
		FlowTui2 tui = new FlowTui2();
		
		context.put("tui", tui);
		
		FlowObserver flowObserver = new FlowObserver();
		
		Flow flow = Flow.builder()
							.id(definitions.getProcess().getId())
							.name(definitions.getProcess().getName())
							.stateMachineObserver(flowObserver)
						.build();
		
		FlowObserversRegistry.Builder builder = FlowObserversRegistry.builder();
		
		addEnterStateObservers(builder, idToState, taskIdToEnterStateExtensionElementPropertieValues, flowConfiguration, context);
		addLeaveStateObservers(builder, idToState, taskIdToLeaveStateExtensionElementPropertieValues, flowConfiguration);
		
		addPrepareTransitionObservers(builder, idToTransition, sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues, flowConfiguration, flow, objectStorage);
		addPerformTransitionObservers(builder, idToTransition, sequenceFlowIdToPerformTransitionExtensionElementPropertieValues, flowConfiguration, flow, objectStorage);
		addDisposeTransitionObservers(builder, idToTransition, sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues, flowConfiguration, flow, objectStorage);
		
		FlowObserversRegistry flowObserversRegistry = builder.build();

		flowObserver.setFlowObserversRegistry(flowObserversRegistry);

		tui.setFlow(flow);
		
		State startingState = idToState.get(startSequenceFlow.getTargetRef());
		flow.startingState(startingState);
	}

	////////////////////// Observers creation
	private static void addEnterStateObservers(
			Builder builder,
			Map<String, State> idToState,
			ImmutableMultiValueMap<String, String> taskIdToEnterStateExtensionElementPropertieValues,
			FlowConfiguration flowConfiguration,
			Map<Object, Object> context) {
		taskIdToEnterStateExtensionElementPropertieValues.keySet().forEach(taskId -> {
			StateObserver[] stateObservers = taskIdToEnterStateExtensionElementPropertieValues.get(taskId)
					.stream().map(value -> textToStateObserver(value, flowConfiguration, context)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new StateObserver[] {});
			builder.addEnterStateObservers(idToState.get(taskId), stateObservers);
		});
	}

	private static void addLeaveStateObservers(
			Builder builder,
			Map<String, State> idToState,
			ImmutableMultiValueMap<String, String> taskIdToLeaveStateExtensionElementPropertieValues,
			FlowConfiguration flowConfiguration) {
		taskIdToLeaveStateExtensionElementPropertieValues.keySet().forEach(taskId -> {
			StateObserver[] stateObservers = taskIdToLeaveStateExtensionElementPropertieValues.get(taskId)
					.stream().map(value -> textToStateObserver(value, flowConfiguration)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new StateObserver[] {});
			builder.addLeaveStateObservers(idToState.get(taskId), stateObservers);
		});
	}
	
	private static StateObserver textToStateObserver(String text, FlowConfiguration flowConfiguration) {
		return textToStateObserver(text, flowConfiguration, null);
	}
	
	private static StateObserver textToStateObserver(String text, FlowConfiguration flowConfiguration, Map<Object, Object> context) {
		String trimedText = text.trim();
		String extensionValueType = extractExtensionValueType(trimedText);
		Object[] parameters = extractExtensionValueTypeParameters(trimedText);
		return (StateObserver) flowConfiguration.getIdToHandler().getHandler(extensionValueType).handle(toObjectArray(parameters, context));
	}

	private static String extractExtensionValueType(String trimedText) {
		return trimedText.substring(0, trimedText.indexOf('('));
	}

	private static Object[] extractExtensionValueTypeParameters(String trimedText) {
		String extensionValueTypeParameters = trimedText.substring(trimedText.indexOf('(') + 1, trimedText.lastIndexOf(')'));
		return Stream.of(extensionValueTypeParameters.split(","))
				.map(value -> value.trim().substring(value.trim().indexOf('"') + 1, value.trim().lastIndexOf('"')))
				.collect(Collectors.toList()).toArray(new Object[] {});
	}

	private static void addPrepareTransitionObservers(
			Builder builder,
			Map<String, Transition> idToTransition,
			ImmutableMultiValueMap<String, String> sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues,
			FlowConfiguration flowConfiguration,
			Flow flow,
			ObjectStorage objectStorage) {
		sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues.keySet().forEach(sequenceFlowId -> {
			Transition transition = idToTransition.get(sequenceFlowId);
			TransitionObserver[] transitionObservers = sequenceFlowIdToPrepareTransitionExtensionElementPropertieValues.get(sequenceFlowId)
					.stream().map(value -> textToTransitionObserver(flowConfiguration, value, flow, transition, objectStorage)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new TransitionObserver[] {});
			builder.addPrepareTransitionObserver(transition, transitionObservers);
		});
	}

	private static void addPerformTransitionObservers(
			Builder builder,
			Map<String, Transition> idToTransition,
			ImmutableMultiValueMap<String, String> sequenceFlowIdToPerformTransitionExtensionElementPropertieValues,
			FlowConfiguration flowConfiguration,
			Flow flow,
			ObjectStorage objectStorage) {
		sequenceFlowIdToPerformTransitionExtensionElementPropertieValues.keySet().forEach(sequenceFlowId -> {
			Transition transition = idToTransition.get(sequenceFlowId);
			TransitionObserver[] transitionObservers = sequenceFlowIdToPerformTransitionExtensionElementPropertieValues.get(sequenceFlowId)
					.stream().map(value -> textToTransitionObserver(flowConfiguration, value, flow, transition, objectStorage)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new TransitionObserver[] {});
			builder.addPerformTransitionObserver(transition, transitionObservers);
		});
	}

	private static void addDisposeTransitionObservers(
			Builder builder,
			Map<String, Transition> idToTransition,
			ImmutableMultiValueMap<String, String> sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues,
			FlowConfiguration flowConfiguration,
			Flow flow,
			ObjectStorage objectStorage) {
		sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues.keySet().forEach(sequenceFlowId -> {
			Transition transition = idToTransition.get(sequenceFlowId);
			TransitionObserver[] transitionObservers = sequenceFlowIdToDisposeTransitionExtensionElementPropertieValues.get(sequenceFlowId)
					.stream().map(value -> textToTransitionObserver(flowConfiguration, value, flow, transition, objectStorage)).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new TransitionObserver[] {});
			builder.addDisposeTransitionObserver(transition, transitionObservers);
		});
	}

	private static TransitionObserver textToTransitionObserver(FlowConfiguration flowConfiguration, String text, Flow flow, Transition transition, ObjectStorage objectStorage) {
		String trimedText = text.trim();
		String extensionValueType = extractExtensionValueType(trimedText);
		Object[] parameters = extractExtensionValueTypeParameters(trimedText);
		return (TransitionObserver) flowConfiguration.getIdToHandler().getHandler(extensionValueType).handle(toObjectArray(parameters, objectStorage, flow, transition));
	}

	////////////////////// Core State Transition creation
	private static Map<String, State> createStates(Collection<Task> tasks) {
		Map<String, State> idToState = new HashMap<>();
		tasks.forEach(task -> idToState.put(
			task.getId(),
			FlowState.builder()
				.id(task.getId())
				.name(task.getName())
			.build())
		);
		return idToState;
	}
	
	private static Map<String, Transition> createTransitions(Map<String, State> idToState, Collection<SequenceFlow> sequenceFlows) {
		Map<String, Transition> idToTransition = new HashMap<>();
		sequenceFlows.forEach(sequenceFlow -> idToTransition.put(
			sequenceFlow.getId(),
			FlowTransition.builder()
					.from(idToState.get(sequenceFlow.getSourceRef()))
					.to(idToState.get(sequenceFlow.getTargetRef()))
					.id(sequenceFlow.getId())
					.name(sequenceFlow.getName())
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
				.collect(Collectors.toMap(MainTuiApplicationWithBPMN2::taskKeyMapper, MainTuiApplicationWithBPMN2::taskValueMapper));
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
				.collect(Collectors.toMap(MainTuiApplicationWithBPMN2::sequenceFlowKeyMapper, MainTuiApplicationWithBPMN2::sequenceFlowValueMapper));
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
	
	private static ImmutableMultiValueMap<String, String> extractTaskExtensionElementPropertieValues(String extensionId, Collection<Task> tasks) {
		PropertyNameMatcher propertyNameMatcher = new PropertyNameMatcher(extensionId);
		ImmutableMultiValueMap<String, String> taskIdToExtensionElementPropertieValues = new ImmutableMultiValueMapImpl<>();
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
	


	private static ImmutableMultiValueMap<String, String> extractSequenceFlowExtensionElementPropertieValues(String extensionId, Collection<SequenceFlow> sequenceFlows) {
		PropertyNameMatcher propertyNameMatcher = new PropertyNameMatcher(extensionId);
		ImmutableMultiValueMap<String, String> sequenceFlowIdToExtensionElementPropertieValues = new ImmutableMultiValueMapImpl<>();
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

	private static Object[] toObjectArray(Object[] parameters, Map<Object, Object> context) {
		List<Object> objects = new ArrayList<>(Arrays.asList(parameters));
		objects.add(context);
		return objects.toArray();
	}

	private static Object[] toObjectArray(Object[] parameters, ObjectStorage objectStorage, Flow flow, Transition transition) {
		List<Object> objects = new ArrayList<>(Arrays.asList(parameters));
		objects.add(objectStorage);
		objects.add(flow);
		objects.add(transition);
		return objects.toArray();
	}
}