#!/usr/bin/env groovy

@GrabResolver(name='quality-eval', root='http://artifactory.qe.yandex-team.ru/quality-eval')
@Grab('ru.yandex.schedulers.cluster-service:cluster-service-client:3.5.2893.0')  
import org.springframework.web.context.support.XmlWebApplicationContext;
import ru.yandex.schedulers.cluster.api.ClusterServiceRestful;
import ru.yandex.qe.cluster.client.ClusterClient;
import ru.yandex.iss.*;
import ru.yandex.iss.thrift.ApplyTargetStateResponse;
import ru.yandex.qe.cluster.client.ClusterClient;
import ru.yandex.qe.spring.profiles.Profiles;
import ru.yandex.schedulers.cluster.api.*;
import ru.yandex.schedulers.cluster.api.computing.*;
import ru.yandex.schedulers.cluster.api.computing.ComputingResources;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableList;
import java.util.*;

///////////////////////////////////////////////////////////////////////
groupOwner = new Owner(new OwnerId("sterligovak"), 0, ProjectId.DEFAULT);
schedulerId = new SchedulerId("xcalc_starter_script");

Workload createWorkload(def hostId){
	Entity xcalcEntity = new Instance();
	xcalcEntity.setSlot(new Slot("qloud_xcalc", hostId.id).toString());
	xcalcEntity.setTargetState(Goal.ACTIVE.name());

	SortedMap<String, Resourcelike> resources = new TreeMap<>();

	Resource image = new Resource();
	image.setUrls(ImmutableList.of("rbtorrent:5255389c7ebf1963221bcc86926d6028d42d0476"));
	image.setUuid("5255389c7ebf1963221bcc86926d6028d42d0476");
	resources.put("image.tar", image);

	Resource launcher = new Resource();
	launcher.setUrls(ImmutableList.of("rbtorrent:f146f428f304bf75b028240921f428ba0af877d4"));
	launcher.setUuid("f146f428f304bf75b028240921f428ba0af877d4");
	resources.put("launcher", launcher);

	Resource iss_hook_install = new Resource();
	iss_hook_install.setUrls(ImmutableList.of("rbtorrent:85b2e4ed29b57fcf6b76d0d2b0fb2670be7a0fd5"));
	iss_hook_install.setUuid("85b2e4ed29b57fcf6b76d0d2b0fb2670be7a0fd5");
	resources.put("iss_hook_install", iss_hook_install);

	Resource iss_hook_start = new Resource();
	iss_hook_start.setUrls(ImmutableList.of("rbtorrent:f7f1dbd3de6185743933adefbff74c5b72b5592f"));
	iss_hook_start.setUuid("f7f1dbd3de6185743933adefbff74c5b72b5592f");
	resources.put("iss_hook_start", iss_hook_start);

	xcalcEntity.setResources(resources);

	long ram = 8l << 30;
	//xcalcEntity.container.constraints["cpu_guarantee"] = "0"
	//xcalcEntity.container.constraints["cpu_limit"] = "50"
	xcalcEntity.container.constraints["iss_hook_start.memory_guarantee"] = Long.toString(ram)
	xcalcEntity.container.constraints["memory_limit"] = Long.toString(2 * ram)

	ComputingResources compRes = ComputingResourcesBuilder.start()
	        .add(new CPUPower(0))
	        .add(new RAM(ram))
	        .add(new HDDSpace(25l << 30))
	        .add(new NetworkBandwidth(20L << 20))
	        .build();
	return new Workload(xcalcEntity, groupOwner, compRes, schedulerId, Collections.emptyMap());
}
///////////////////////////////////////////////////////////////////////

//System.properties.put("clusterapi.url", "http://clusterapi.search.yandex.net:9200");
System.properties.put("clusterapi.url", "https://dev-clusterapi.qloud.yandex-team.ru");

def webApplicationContext = new XmlWebApplicationContext();
webApplicationContext.setConfigLocations("classpath*:spring/cluster-api-client.xml");
webApplicationContext.refresh();

def clusterService = (ClusterServiceRestful) webApplicationContext.getBean("clusterService");
def clusterClient = new ClusterClient(clusterService);

////////////////////////////////////////////////////////////////////////////
def clusterState = clusterClient.getStateAsync(0, "", [] as Set).join();

def hostTransitions = clusterState.hosts
    //.findAll({hostId, host -> hostId != "s1-1015.qloud.yandex.net"})
	.collect({hostId, host ->
		println hostId
		def hostStateEtag = host.hostStateEtag;
		def xCalcWorkload = createWorkload(hostId)
		return new Transition(hostId, hostStateEtag, ImmutableSortedSet.of(xCalcWorkload));
	})

GroupId groupId = new GroupId("qloud_xcalc");
GroupOperationId goId = GroupOperationId.generate();

GroupTransition groupTransition = new GroupTransition(goId, groupId, groupOwner, hostTransitions as SortedSet);
SortedSet<GroupTransition> transitions = ImmutableSortedSet.of(groupTransition);
ApplyResultEither result = clusterClient.applyAsync(transitions, new SchedulerSignature(schedulerId, "Starting xcalc on qloud")).join().get(groupId);
if(result.success){
	println "Deployed!"
	return true
} else {
	println result.ex
}

