/**
 * Copyright 2019 Red Hat, Inc, and individual contributors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package id.global.iris.asyncapi.api;

import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.Config;

import id.global.iris.asyncapi.api.util.ConfigUtil;
import id.global.iris.asyncapi.spec.AAIConfig;

/**
 * Implementation of the {@link AsyncApiConfig} interface that gets config information from a
 * standard MP Config object.
 *
 * @author eric.wittmann@gmail.com
 */
public class AsyncApiConfigImpl implements AsyncApiConfig {

    private Config config;

    private String modelReader;
    private String filter;
    private Boolean scanDisable;
    private Set<String> scanPackages;
    private Set<String> scanClasses;
    private Set<String> scanExcludePackages;
    private Set<String> scanExcludeClasses;
    private Set<String> servers;
    private Boolean scanDependenciesDisable;
    private Set<String> scanDependenciesJars;
    private Boolean schemaReferencesEnable;
    private String customSchemaRegistryClass;
    private Set<String> excludeFromSchemas;
    private String projectVersion;

    /**
     * Constructor.
     *
     * @param config MicroProfile Config instance
     */
    public AsyncApiConfigImpl(Config config) {
        this.config = config;
    }

    /**
     * @return the MP config instance
     */
    protected Config getConfig() {
        // We cannot use ConfigProvider.getConfig() as the archive is not deployed yet - TCCL cannot be set
        return config;
    }

    /**
     * @see AsyncApiConfig#modelReader()
     */
    @Override
    public String modelReader() {
        if (modelReader != null) {
            return modelReader;
        }
        modelReader = getConfig().getOptionalValue(AAIConfig.MODEL_READER, String.class).orElse(null);
        return modelReader;
    }

    /**
     * @see AsyncApiConfig#filter()
     */
    @Override
    public String filter() {
        if (filter != null) {
            return filter;
        }
        filter = getConfig().getOptionalValue(AAIConfig.FILTER, String.class).orElse(null);
        return filter;
    }

    /**
     * @see AsyncApiConfig#scanDisable()
     */
    @Override
    public boolean scanDisable() {
        if (scanDisable != null) {
            return scanDisable;
        }
        scanDisable = getConfig().getOptionalValue(AAIConfig.SCAN_DISABLE, Boolean.class).orElse(false);
        return scanDisable;
    }

    /**
     * @return
     * @see AsyncApiConfig#scanPackages()
     */
    @Override
    public Set<String> scanPackages() {
        if (scanPackages != null) {
            return scanPackages;
        }
        String packages = getConfig().getOptionalValue(AAIConfig.SCAN_PACKAGES, String.class).orElse(null);
        scanPackages = ConfigUtil.asCsvSet(packages);
        return scanPackages;
    }

    /**
     * @return
     * @see AsyncApiConfig#scanPackages()
     */
    @Override
    public Pattern scanPackagesPattern() {
        return ConfigUtil.patternFromSet(scanPackages());
    }

    /**
     * @see AsyncApiConfig#scanClasses()
     */
    @Override
    public Set<String> scanClasses() {
        if (scanClasses != null) {
            return scanClasses;
        }
        String classes = getConfig().getOptionalValue(AAIConfig.SCAN_CLASSES, String.class).orElse(null);
        scanClasses = ConfigUtil.asCsvSet(classes);
        return scanClasses;
    }

    public Pattern scanClassesPattern() {
        return ConfigUtil.patternFromSet(scanClasses());
    }

    /**
     * @see AsyncApiConfig#scanExcludePackages()
     */
    @Override
    public Set<String> scanExcludePackages() {
        if (scanExcludePackages != null) {
            return scanExcludePackages;
        }
        String packages = getConfig().getOptionalValue(AAIConfig.SCAN_EXCLUDE_PACKAGES, String.class).orElse(null);
        scanExcludePackages = ConfigUtil.asCsvSet(packages);
        return scanExcludePackages;
    }

    @Override
    public Pattern scanExcludePackagesPattern() {
        return ConfigUtil.patternFromSet(scanExcludePackages());
    }

    /**
     * @see AsyncApiConfig#scanExcludeClasses()
     */
    @Override
    public Set<String> scanExcludeClasses() {
        if (scanExcludeClasses != null) {
            return scanExcludeClasses;
        }
        String classes = getConfig().getOptionalValue(AAIConfig.SCAN_EXCLUDE_CLASSES, String.class).orElse(null);
        scanExcludeClasses = ConfigUtil.asCsvSet(classes);
        return scanExcludeClasses;
    }

    @Override
    public Pattern scanExcludeClassesPattern() {
        return ConfigUtil.patternFromSet(scanExcludeClasses());
    }

    /**
     * @see AsyncApiConfig#servers()
     */
    @Override
    public Set<String> servers() {
        if (servers != null) {
            return servers;
        }
        String theServers = getConfig().getOptionalValue(AAIConfig.SERVERS, String.class).orElse(null);
        servers = ConfigUtil.asCsvSet(theServers);
        return servers;
    }

    /**
     * @see AsyncApiConfig#scanDependenciesDisable()
     */
    @Override
    public boolean scanDependenciesDisable() {
        if (scanDependenciesDisable != null) {
            return scanDependenciesDisable;
        }
        scanDependenciesDisable = getConfig().getOptionalValue(AsyncApiConstants.SCAN_DEPENDENCIES_DISABLE, Boolean.class)
                .orElse(false);
        return scanDependenciesDisable;
    }

    /**
     * @see AsyncApiConfig#scanDependenciesJars()
     */
    @Override
    public Set<String> scanDependenciesJars() {
        if (scanDependenciesJars != null) {
            return scanDependenciesJars;
        }
        String classes = getConfig().getOptionalValue(AsyncApiConstants.SCAN_DEPENDENCIES_JARS, String.class).orElse(null);
        scanDependenciesJars = ConfigUtil.asCsvSet(classes);
        return scanDependenciesJars;
    }

    @Override
    public boolean schemaReferencesEnable() {
        if (schemaReferencesEnable != null) {
            return schemaReferencesEnable;
        }
        schemaReferencesEnable = getConfig().getOptionalValue(AsyncApiConstants.SCHEMA_REFERENCES_ENABLE, Boolean.class)
                .orElse(false);
        return schemaReferencesEnable;
    }

    @Override
    public String customSchemaRegistryClass() {
        if (customSchemaRegistryClass != null) {
            return customSchemaRegistryClass;
        }
        customSchemaRegistryClass = getConfig()
                .getOptionalValue(AsyncApiConstants.CUSTOM_SCHEMA_REGISTRY_CLASS, String.class).orElse(null);
        return customSchemaRegistryClass;
    }

    @Override
    public Set<String> excludeFromSchemas() {
        if (excludeFromSchemas != null) {
            return excludeFromSchemas;
        }
        String packages = getConfig()
                .getOptionalValue(AsyncApiConstants.EXCLUDE_FROM_SCHEMAS, String.class).orElse(null);
        excludeFromSchemas = ConfigUtil.asCsvSet(packages);
        return excludeFromSchemas;
    }

    @Override
    public String projectVersion() {
        if (projectVersion != null) {
            return projectVersion;
        }
        projectVersion = getConfig().getOptionalValue(AsyncApiConstants.PROJECT_VERSION, String.class).orElse(null);
        return projectVersion;
    }
}
