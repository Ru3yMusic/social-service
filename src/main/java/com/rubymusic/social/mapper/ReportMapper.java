package com.rubymusic.social.mapper;

import com.rubymusic.social.dto.ReportResponse;
import com.rubymusic.social.model.Report;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReportMapper {

    ReportResponse toDto(Report report);

    List<ReportResponse> toDtoList(List<Report> reports);
}
