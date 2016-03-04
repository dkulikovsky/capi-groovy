#!/usr/bin/env groovy
/**
 * Created by dkulikovsky on 04.03.16.
 */
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedSet
import org.apache.ivy.plugins.repository.Resource
import org.springframework.web.context.support.XmlWebApplicationContext
@GrabResolver(name='quality-eval', root='http://artifactory.yandex.net/artifactory/qe')
//@Grab('ru.yandex.schedulers.cluster-service:cluster-service-client:3.5.3003.0')
@Grab('ru.yandex.schedulers.job-service:job-service-api:4.4.1650')
import org.springframework.web.context.support.XmlWebApplicationContext
import ru.yandex.iss.*
import ru.yandex.qe.cluster.client.ClusterClient
import ru.yandex.schedulers.cluster.api.*
import ru.yandex.schedulers.cluster.api.ClusterServiceRestful
import ru.yandex.schedulers.cluster.api.computing.*
import ru.yandex.schedulers.cluster.api.computing.ComputingResources
import ru.yandex.schedulers.job.api.*
import ru.yandex.schedulers.job.core.*

///////////////////////////////////////////////////////////////////////
groupOwner = new Owner(new OwnerId("dkulikovsky"), 0, ProjectId.DEFAULT);
schedulerId = new SchedulerId("dkulikovsky_fun_in_production");

public ru.yandex.iss.Resource mkResource(String startURL, String uuid) {
    ru.yandex.iss.Resource resource = new ru.yandex.iss.Resource();
    resource.setUrls(ImmutableList.of(startURL));
    resource.setUuid(uuid);
    return resource;
}

Workload createWorkload(def hostId){
    Entity xcalcEntity = new Instance();
    xcalcEntity.setSlot(new Slot("dkulikovsky_scheduler", hostId.id).toString());
    xcalcEntity.setTargetState(Goal.ACTIVE.name());

    SortedMap<String, Resourcelike> resources = new TreeMap<>();

    ru.yandex.iss.Resource iss_hook_start = mkResource("https://keyvalue.qloud.yandex-team.ru/api/versioned/keyvalue/82373b02d9400b56673d9a5f55993b89?version=56d9c30f158e348a67e6928b",
            "iss_start_hook_simple_bash_sleep");
    resources.put("iss_hook_start", iss_hook_start);

    xcalcEntity.setResources(resources);

    long ram = 8l << 30;
    //xcalcEntity.container.constraints["cpu_guarantee"] = "0"
    //xcalcEntity.container.constraints["cpu_limit"] = "50"
    xcalcEntity.container.constraints["iss_hook_start.memory_guarantee"] = Long.toString(ram)
    xcalcEntity.container.constraints["memory_limit"] = Long.toString(2 * ram)
    xcalcEntity.container.constraints["meta.net"] = "macvlan vlan1478 eth0; macvlan vlan767 vlan767"
    xcalcEntity.container.constraints["meta.ip"] = "eth0 2a02:6b8:c02:1:0:1478:bd29:8bbc"
    xcalcEntity.container.constraints["meta.hostname"] = "i-bd298bbcb66f.qloud-c.yandex.net"
    xcalcEntity.container.constraints["meta.virt_mode"] = "os"
    xcalcEntity.container.constraints["meta.command"] = "/sbin/init"


    // build volumes

    List<Resource> layers = new ArrayList<>(
            ImmutableList.of(
                    mkResource("rbtorrent:be403505a2b6b5822e9915faccff4815cc324616",
                            "ubuntu_base"),
                    mkResource("rbtorrent:6f16e160662866ca00bdb89462adacfe83b6efd3",
                            "ssh"),
                    mkResource("rbtorrent:2bf2f673446d35ab63fa2f6e8f79c1d5e975a030",
                            "net-tools")
            ));

    Map<String, String> volumeProperties = new HashMap<>();

    Volume volume = VolumeBuilder.start()
            .withQuota(2 * 1024*1024)
            .withQuotaCwd(2 * 1024 * 1024)
            .withLayers(layers)
            .withMountPoint("/")
            .withProperties(volumeProperties)
            .build();

    List<Volume> volumeList = Collections.singletonList(volume);
    xcalcEntity.setVolumes(volumeList);

    ComputingResources compRes = ComputingResourcesBuilder.start()
            .add(new CPUPower(0))
            .add(new RAM(ram))
            .add(new HDDSpace(25l << 30))
            .add(new NetworkBandwidth(20L << 20))
            .build();
    return new Workload(xcalcEntity, groupOwner, compRes, schedulerId, Collections.emptyMap());
}

///////////////////////////////////////////////////////////////////////

System.properties.put("clusterapi.url", "https://dev-clusterapi.qloud.yandex-team.ru");

def webApplicationContext = new XmlWebApplicationContext();
webApplicationContext.setConfigLocations("classpath*:spring/cluster-api-client.xml");
webApplicationContext.refresh();

def clusterService = (ClusterServiceRestful) webApplicationContext.getBean("clusterService");
def clusterClient = new ClusterClient(clusterService);

////////////////////////////////////////////////////////////////////////////
def clusterState = clusterClient.getStateAsync(0, "", [] as Set).join();

/*
println("searching for host s1-1111.qloud.yandex.net")
clusterState.hosts.findAll({hostId, host -> hostId =~ "s1-1111.qloud.yandex.net"}).collect({hostId, host ->
    println(hostId)
})
*/

def hostTransitions = clusterState.hosts.findAll({hostId, host -> hostId =~ "s1-1111.qloud.yandex.net"})
        .collect({hostId, host ->
    println hostId
    def hostStateEtag = host.hostStateEtag;
    def xCalcWorkload = createWorkload(hostId)
    return new Transition(hostId, hostStateEtag, ImmutableSortedSet.of(xCalcWorkload));
})


GroupId groupId = new GroupId("dkulikovsky");
GroupOperationId goId = GroupOperationId.generate();

GroupTransition groupTransition = new GroupTransition(goId, groupId, groupOwner, hostTransitions as SortedSet);
SortedSet<GroupTransition> transitions = ImmutableSortedSet.of(groupTransition);
ApplyResultEither result = clusterClient.applyAsync(transitions, new SchedulerSignature(schedulerId, "Starting dkulikovsky funny job")).join().get(groupId);
if(result.success){
    println "Deployed!"
    return true
} else {
    println result.ex
}