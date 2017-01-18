/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.ui.ws;

import org.sonar.api.config.Settings;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService.NewController;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.api.web.page.Page;
import org.sonar.core.config.WebConstants;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.server.ui.PageRepository;
import org.sonar.server.user.UserSession;

public class SettingsAction implements NavigationWsAction {

  private final PageRepository pageRepository;
  private final Settings settings;
  private final UserSession userSession;

  public SettingsAction(PageRepository pageRepository, Settings settings, UserSession userSession) {
    this.pageRepository = pageRepository;
    this.settings = settings;
    this.userSession = userSession;
  }

  @Override
  public void define(NewController context) {
    context.createAction("settings")
      .setDescription("Get configuration information for the settings page:" +
        "<ul>" +
        "  <li>List plugin-contributed pages</li>" +
        "  <li>Show update center (or not)</li>" +
        "</ul>")
      .setHandler(this)
      .setInternal(true)
      .setResponseExample(getClass().getResource("settings-example.json"))
      .setSince("5.2");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    boolean isAdmin = userSession.hasPermission(GlobalPermissions.SYSTEM_ADMIN);

    JsonWriter json = response.newJsonWriter().beginObject();
    json.prop("showUpdateCenter", isAdmin && settings.getBoolean(WebConstants.SONAR_UPDATECENTER_ACTIVATE));
    json.prop("showProvisioning", userSession.hasPermission(GlobalPermissions.PROVISIONING));

    json.name("extensions").beginArray();
    if (isAdmin) {
      for (Page page : pageRepository.getGlobalPages(true)) {
        json.beginObject()
          .prop("key", page.getKey())
          .prop("name", page.getName())
          .endObject();
      }
    }
    json.endArray();

    json.endObject().close();
  }
}