/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.apicatalog.services.cached;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.zowe.apiml.apicatalog.model.APIContainer;
import org.zowe.apiml.apicatalog.model.APIService;
import org.zowe.apiml.apicatalog.model.SemanticVersion;
import org.zowe.apiml.eurekaservice.client.util.EurekaMetadataParser;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.constants.CoreService;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.apiml.product.routing.RoutedServices;
import org.zowe.apiml.product.routing.ServiceType;
import org.zowe.apiml.product.routing.transform.TransformService;
import org.zowe.apiml.product.routing.transform.URLTransformationException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.toList;
import static org.zowe.apiml.constants.EurekaMetadataDefinition.*;

/**
 * Caching service for eureka services
 */
@Slf4j
@Service
@CacheConfig(cacheNames = {"products"})
public class CachedProductFamilyService {

    @InjectApimlLogger
    private final ApimlLogger apimlLog = ApimlLogger.empty();

    private final Integer cacheRefreshUpdateThresholdInMillis;

    private final CachedServicesService cachedServicesService;
    private final EurekaMetadataParser metadataParser = new EurekaMetadataParser();
    private final TransformService transformService;

    private final Map<String, APIContainer> products = new HashMap<>();


    public CachedProductFamilyService(CachedServicesService cachedServicesService,
                                      TransformService transformService,
                                      @Value("${apiml.service-registry.cacheRefreshUpdateThresholdInMillis}")
                                          Integer cacheRefreshUpdateThresholdInMillis) {
        this.cachedServicesService = cachedServicesService;
        this.transformService = transformService;
        this.cacheRefreshUpdateThresholdInMillis = cacheRefreshUpdateThresholdInMillis;
    }

    /**
     * Return all cached service instances
     *
     * @return instances
     */
    @Cacheable
    public Collection<APIContainer> getAllContainers() {
        return products.values();
    }


    /**
     * return cached service instance by id
     *
     * @param id service identifier
     * @return {@link APIContainer}
     */
    public APIContainer getContainerById(String id) {
        return products.get(id);
    }

    /**
     * Retrieve any containers which have had their details updated after the threshold figure
     * If performance is slow then possibly cache the result and evict after 'n' seconds
     *
     * @return recently updated containers
     */
    public List<APIContainer> getRecentlyUpdatedContainers() {
        return this.products.values().stream().filter(
            container -> {
                boolean isRecent = container.isRecentUpdated(cacheRefreshUpdateThresholdInMillis);
                if (isRecent) {
                    log.debug("Container: " + container.getId() + " last updated: "
                        + container.getLastUpdatedTimestamp().getTime() +
                        " was updated recently");
                }
                return isRecent;
            }).collect(toList());
    }

    /**
     * return a cached service instance from a container
     *
     * @param productFamilyId the service identifier
     * @return instances for this service (might be empty instances collection)
     */
    @Cacheable(key = "#productFamilyId+ #instanceInfo.appName")
    public APIService getContainerService(final String productFamilyId, final InstanceInfo instanceInfo) {
        APIContainer apiContainer = products.get(productFamilyId.toLowerCase());
        Optional<APIService> result = apiContainer.getServices().stream()
            .filter(service -> instanceInfo.getAppName().equalsIgnoreCase(service.getServiceId()))
            .findFirst();
        return result.orElse(null);
    }

    /**
     * Add service to container
     *
     * @param productFamilyId the service identifier
     * @param instanceInfo    InstanceInfo
     */
    @CachePut(key = "#productFamilyId")
    public void addServiceToContainer(final String productFamilyId, final InstanceInfo instanceInfo) {
        APIContainer apiContainer = products.get(productFamilyId);
        // fix - throw error if null
        apiContainer.addService(createAPIServiceFromInstance(instanceInfo));
        products.put(productFamilyId, apiContainer);
    }

    /**
     * Retrieve a container from the cache
     *
     * @param productFamilyId the product family id
     * @return a container
     */
    @Cacheable(key = "#productFamilyId", sync = true)
    public APIContainer getContainer(final String productFamilyId, @NonNull InstanceInfo instanceInfo) {
        return createContainerFromInstance(productFamilyId, instanceInfo);
    }

    /**
     * Return an uncached container for a given family id
     *
     * @param productFamilyId find a container with this family id
     * @return a container (or null)
     */
    public APIContainer retrieveContainer(@NonNull final String productFamilyId) {
        return this.products.get(productFamilyId);
    }

    /**
     * Return any containers which have the given service registered
     *
     * @param serviceId check for this service
     * @return a list of containers
     */
    public List<APIContainer> getContainersForService(final String serviceId) {
        return this.products.values().stream().filter(
            container -> container.getServices().stream().anyMatch(service -> serviceId.equalsIgnoreCase(service.getServiceId()))
        ).collect(toList());
    }


    /**
     * Create a container from a service instance and add it to the cache
     * or update an existing container if the instance version number has increased
     *
     * @param productFamilyId the product family id
     * @param instanceInfo    the service instance
     */
    @CachePut(key = "#productFamilyId")
    public APIContainer createContainerFromInstance(final String productFamilyId, InstanceInfo instanceInfo) {
        APIContainer container = products.get(productFamilyId);
        if (container == null) {
            container = createNewContainerFromService(productFamilyId, instanceInfo);
        } else {
            addServiceToContainer(productFamilyId, instanceInfo);
            container = products.get(productFamilyId);
            checkIfContainerShouldBeUpdatedFromInstance(instanceInfo, container);
        }
        return container;
    }


    /**
     * Try to transform the service homepage url and return it. If it fails,
     * return the original homepage url
     *
     * @param instanceInfo the service instance
     * @return the transformed homepage url
     */
    private String getInstanceHomePageUrl(InstanceInfo instanceInfo) {
        String instanceHomePage = instanceInfo.getHomePageUrl();

        //Gateway homePage is used to hold DVIPA address and must not be modified
        if (instanceHomePage != null
            && !instanceHomePage.isEmpty()
            && !instanceInfo.getAppName().equalsIgnoreCase(CoreService.GATEWAY.getServiceId())) {
            RoutedServices routes = metadataParser.parseRoutes(instanceInfo.getMetadata());

            try {
                instanceHomePage = transformService.transformURL(
                    ServiceType.UI,
                    instanceInfo.getVIPAddress(),
                    instanceHomePage,
                    routes);
            } catch (URLTransformationException e) {
                apimlLog.log("org.zowe.apiml.apicatalog.homePageTransformFailed", instanceInfo.getAppName(), e.getMessage());
            }
        }

        log.debug("Homepage URL for {} service is: {}", instanceInfo.getVIPAddress(), instanceHomePage);
        return instanceHomePage;
    }

    /**
     * Create a new container based on information in a new instance
     *
     * @param productFamilyId parent id
     * @param instanceInfo    instance
     * @return a new container
     */
    private APIContainer createNewContainerFromService(String productFamilyId, InstanceInfo instanceInfo) {
        Map<String, String> instanceInfoMetadata = instanceInfo.getMetadata();
        String title = instanceInfoMetadata.get(CATALOG_TITLE);
        String description = instanceInfoMetadata.get(CATALOG_DESCRIPTION);
        String version = instanceInfoMetadata.get(CATALOG_VERSION);
        APIContainer container = new APIContainer();
        container.setStatus("UP");
        container.setId(productFamilyId);
        container.setDescription(description);
        container.setTitle(title);
        container.setVersion(version);
        log.debug("updated Container cache with product family: " + productFamilyId + ": " + title);

        // create API Service from instance and update container last changed date
        container.addService(createAPIServiceFromInstance(instanceInfo));
        products.put(productFamilyId, container);
        return container;
    }

    /**
     * Compare the version of the parent in the given instance
     * If the version is greater, then update the parent
     *
     * @param instanceInfo service instance
     * @param container    parent container
     */
    private void checkIfContainerShouldBeUpdatedFromInstance(InstanceInfo instanceInfo, APIContainer container) {
        String versionFromInstance = instanceInfo.getMetadata().get(CATALOG_VERSION);
        // if the instance has a parent version
        if (versionFromInstance != null) {
            final SemanticVersion instanceVer = new SemanticVersion(versionFromInstance);
            SemanticVersion containerVer;
            if (container.getVersion() == null) {
                containerVer = new SemanticVersion("0.0.0");
            } else {
                containerVer = new SemanticVersion(container.getVersion());
            }

            // Only update if the instance version is greater than the container version
            int result = instanceVer.compareTo(containerVer);
            if (result > 0) {
                container.setVersion(versionFromInstance);
                String title = instanceInfo.getMetadata().get(CATALOG_TITLE);
                String description = instanceInfo.getMetadata().get(CATALOG_DESCRIPTION);
                if (!container.getTitle().equals(title)) {
                    container.setTitle(title);
                }
                if (!container.getDescription().equals(description)) {
                    container.setDescription(description);
                }
                container.updateLastUpdatedTimestamp();
            }
        }
    }

    /**
     * Create a APIService object using the instances metadata
     *
     * @param instanceInfo the service instance
     * @return a APIService object
     */
    private APIService createAPIServiceFromInstance(InstanceInfo instanceInfo) {
        boolean secureEnabled = instanceInfo.isPortEnabled(InstanceInfo.PortType.SECURE);

        String instanceHomePage = getInstanceHomePageUrl(instanceInfo);
        return new APIService(
            instanceInfo.getAppName().toLowerCase(),
            instanceInfo.getMetadata().get(SERVICE_TITLE),
            instanceInfo.getMetadata().get(SERVICE_DESCRIPTION),
            secureEnabled, instanceHomePage);
    }

    /**
     * Update a containers details using a service's metadata
     *
     * @param productFamilyId the product family id of the container
     * @param instanceInfo    the service instance
     */
    @CacheEvict(key = "#productFamilyId")
    public void updateContainerFromInstance(String productFamilyId, InstanceInfo instanceInfo) {
        createContainerFromInstance(productFamilyId, instanceInfo);
    }

    /**
     * Save a containers details using a service's metadata
     *
     * @param productFamilyId the product family id of the container
     * @param instanceInfo    the service instance
     */
    @CachePut(key = "#productFamilyId")
    public APIContainer saveContainerFromInstance(String productFamilyId, InstanceInfo instanceInfo) {
        APIContainer container = products.get(productFamilyId);
        if (container == null) {
            container = createNewContainerFromService(productFamilyId, instanceInfo);
        } else {
            Set<APIService> apiServices = container.getServices();
            APIService service = createAPIServiceFromInstance(instanceInfo);
            apiServices.remove(service);

            apiServices.add(service);
            container.setServices(apiServices);
            //update container
            String versionFromInstance = instanceInfo.getMetadata().get(CATALOG_VERSION);
            String title = instanceInfo.getMetadata().get(CATALOG_TITLE);
            String description = instanceInfo.getMetadata().get(CATALOG_DESCRIPTION);

            container.setVersion(versionFromInstance);
            container.setTitle(title);
            container.setDescription(description);
            container.updateLastUpdatedTimestamp();

            products.put(productFamilyId, container);
        }

        return container;
    }

    /**
     * Update the summary totals for a container based on it's running services
     *
     * @param apiContainer calculate totals for this container
     */
    public void calculateContainerServiceTotals(APIContainer apiContainer) {
        final AtomicInteger activeServices = new AtomicInteger(0);
        if (apiContainer.getServices() != null) {
            activeServices.set(apiContainer.getServices().size());
            apiContainer.getServices().forEach(apiService -> {
                Application service = this.cachedServicesService.getService(apiService.getServiceId());
                // only use running instances
                if (service != null) {
                    long numInstances = service.getInstances().stream().filter(
                        instance -> instance.getStatus().equals(InstanceInfo.InstanceStatus.UP)).count();
                    if (numInstances == 0) {
                        activeServices.getAndDecrement();
                        apiService.setStatus("DOWN");
                    } else {
                        apiService.setStatus("UP");
                    }
                }
            });
        }

        // set counters for total and active services
        apiContainer.setTotalServices(apiContainer.getServices() == null ? 0 : apiContainer.getServices().size());
        apiContainer.setActiveServices(activeServices.get());

        if (activeServices.get() == 0) {
            apiContainer.setStatus("DOWN");
        } else if (activeServices.get() < apiContainer.getServices().size()) {
            apiContainer.setStatus("WARNING");
        } else {
            apiContainer.setStatus("UP");
        }
    }

    /**
     * Return the number of containers (used for checking if a new container was created)
     *
     * @return the number of containers
     */
    public int getContainerCount() {
        return products.size();
    }
}
