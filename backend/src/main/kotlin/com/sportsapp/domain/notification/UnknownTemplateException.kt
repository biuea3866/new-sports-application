package com.sportsapp.domain.notification

class UnknownTemplateException(templateId: String) :
    RuntimeException("Unknown notification template: $templateId")
