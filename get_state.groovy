#!/usr/bin/env groovy
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedSet
import org.apache.ivy.plugins.repository.Resource
@GrabResolver(name = 'quality-eval', root = 'http://artifactory.yandex.net/artifactory/qe')
@Grab('ru.yandex.schedulers.cluster-service:cluster-service-client:3.5.3003.0')
import org.springframework.web.context.support.XmlWebApplicationContext
@GrabResolver(name='quality-eval', root='http://artifactory.yandex.net/artifactory/qe')
@Grab('ru.yandex.schedulers.cluster-service:cluster-service-client:3.5.3003.0')
import org.springframework.web.context.support.XmlWebApplicationContext
import ru.yandex.iss.*
import ru.yandex.qe.cluster.client.ClusterClient
import ru.yandex.schedulers.cluster.api.*
import ru.yandex.schedulers.cluster.api.ClusterServiceRestful
import ru.yandex.schedulers.cluster.api.computing.*
import ru.yandex.schedulers.cluster.api.computing.ComputingResources
import ru.yandex.schedulers.job.api.*
import ru.yandex.schedulers.job.core.*

public Resource mkResource(String startURL, String uuid) {
    Resource resource = new ru.yandex.iss.Resource();
    resource.setUrls(ImmutableList.of(startURL));
    resource.setUuid(uuid);
    return resource;
}

System.properties.put("clusterapi.url", "https://dev-clusterapi.qloud.yandex-team.ru");

def webApplicationContext = new XmlWebApplicationContext();
webApplicationContext.setConfigLocations("classpath*:spring/cluster-api-client.xml");
webApplicationContext.refresh();

def clusterService = (ClusterServiceRestful) webApplicationContext.getBean("clusterService");
def clusterClient = new ClusterClient(clusterService);

////////////////////////////////////////////////////////////////////////////
def clusterState = clusterClient.getStateAsync(0, "", [] as Set).join();

println("searching for host s1-1111.qloud.yandex.net")
clusterState.hosts.findAll({hostId, host -> hostId =~ "s1-1111.qloud.yandex.net"}).collect({hostId, host ->
                            println(hostId)
})
