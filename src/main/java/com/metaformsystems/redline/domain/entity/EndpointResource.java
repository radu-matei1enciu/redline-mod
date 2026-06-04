package com.metaformsystems.redline.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Map;

@Entity
@Table(name = "endpoint_resources")
public class EndpointResource extends VersionedEntity {

    private String assetId;
    private String endpointUrl;
    private String name;

    @Column(name = "metadata", columnDefinition = "TEXT")
    @Convert(converter = HashMapConverter.class)
    private Map<String, Object> metadata;

    public EndpointResource() {
    }

    public EndpointResource(String assetId, String endpointUrl, String name, Map<String, Object> metadata) {
        this.assetId = assetId;
        this.endpointUrl = endpointUrl;
        this.name = name;
        this.metadata = metadata;
    }

    public String getAssetId() { return assetId; }
    public String getEndpointUrl() { return endpointUrl; }
    public String getName() { return name; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}