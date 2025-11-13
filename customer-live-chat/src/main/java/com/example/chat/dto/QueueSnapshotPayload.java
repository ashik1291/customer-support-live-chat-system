package com.example.chat.dto;

import com.example.chat.domain.QueueEntry;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueSnapshotPayload implements Serializable {

    private List<QueueEntry> entries;
}
