/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.imdb;

import com.omertron.imdbapi.model.ImdbPerson;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.PersonArtworkScanner;
import org.yamj.plugin.api.model.IPerson;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class ImdbPersonArtworkScanner extends AbstractImdbScanner implements PersonArtworkScanner {

    @Override
    public List<ArtworkDTO> getPhotos(IPerson person) {
        String imdbId = getPersonId(person, false);
        if (isNoValidImdbId(imdbId)) {
            return null;
        }
        
        ImdbPerson imdbPerson = imdbApiWrapper.getPerson(imdbId, Locale.US, false);
        if (imdbPerson == null || imdbPerson.getImage() == null) {
            return null;
        }
        
        final ArtworkDTO dto = new ArtworkDTO(getScannerName(), imdbPerson.getImage().getUrl(), imdbId);
        return Collections.singletonList(dto);
    }

}
