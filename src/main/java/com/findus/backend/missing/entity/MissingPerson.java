package com.findus.backend.missing.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import com.findus.backend.common.entity.BaseEntity;
import com.findus.backend.missing.dto.SourcePerson;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name="missing_persons",schema="findus")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MissingPerson extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="source_id",nullable=false,unique=true,length=30,updatable=false) private String sourceId;
    @Column(nullable=false,length=100) private String name;
    @Column(nullable=false,length=3) private String category;
    @Column(columnDefinition="text") private String sex;
    @Column(name="occurrence_date",columnDefinition="text") private String occurrenceDate;
    private Integer age;
    @Column(name="age_now") private Integer ageNow;
    @Column(columnDefinition="text") private String address;
    @Column(columnDefinition="text") private String clothing;
    @Column(columnDefinition="text") private String features;
    @Column(name="photo_url",length=500) private String photoUrl;
    @Column(name="source_available",nullable=false) private boolean sourceAvailable=true;
    @Column(name="last_seen_at",nullable=false) private LocalDateTime lastSeenAt;

    public MissingPerson(SourcePerson source, LocalDateTime seenAt) {
        sourceId=source.sourceId(); apply(source,seenAt);
    }
    public boolean apply(SourcePerson s, LocalDateTime seenAt) {
        boolean changed=!snapshot().equals(s) || !sourceAvailable;
        name=s.name(); category=s.category(); sex=s.sex(); occurrenceDate=s.occurrenceDate();
        age=s.age(); ageNow=s.ageNow(); address=s.address(); clothing=s.clothing();
        features=s.features(); photoUrl=s.photoUrl(); sourceAvailable=true; lastSeenAt=seenAt;
        return changed;
    }
    public void markUnavailable() { sourceAvailable=false; }
    private SourcePerson snapshot() { return new SourcePerson(sourceId,name,category,sex,occurrenceDate,age,ageNow,address,clothing,features,photoUrl); }
}
