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

import java.util.List;
import org.yamj.plugin.api.model.ISeries;
import org.yamj.plugin.api.trailer.SeriesTrailerScanner;
import org.yamj.plugin.api.trailer.TrailerDTO;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class ImdbSeriesTrailerScanner extends AbstractImdbTrailerScanner implements SeriesTrailerScanner {

    @Override
    public List<TrailerDTO> scanForTrailer(ISeries series) {
        String imdbId = getSeriesId(series, false);
        return getTrailerDTOS(imdbId);
    }
}
