/**
 * Bounded contexts of the EduShift modular monolith.
 * <p>
 * Each subpackage is an independent module with its own controller, service, repository,
 * entity, DTO, mapper, events, listener, validator and config layers.
 * <p>
 * Cross-module communication must use domain events or shared contracts — never direct
 * imports between module service/entity/repository packages.
 */
package com.edushift.modules;
