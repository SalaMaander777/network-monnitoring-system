package ru.netmon.contract.events;

public record DeviceChanged(long deviceId, ChangeType change) { public enum ChangeType { CREATED, UPDATED, DELETED } }
