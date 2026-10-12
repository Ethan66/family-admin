package com.ethan.familyadmin.movie;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@TableName("movie")
public class MovieEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private String title;
    private String type;
    private Integer releaseYear;
    private String director;
    private Integer minutes;
    private BigDecimal rating;
    private String watchStatus;
    private Integer progress;
    private String description;
    private Long coverFileId;
}
