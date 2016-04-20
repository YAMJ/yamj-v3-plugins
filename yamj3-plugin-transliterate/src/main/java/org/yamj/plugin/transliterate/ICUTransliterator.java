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
package org.yamj.plugin.transliterate;

import org.yamj.plugin.api.transliteration.Transliterator;
import ro.fortsoft.pf4j.Extension;

@Extension
public class ICUTransliterator implements Transliterator {

    private com.ibm.icu.text.Transliterator useTransliterator;
    
    private com.ibm.icu.text.Transliterator getTransliterator() {
        if (useTransliterator == null) {
            useTransliterator = com.ibm.icu.text.Transliterator.getInstance("NFD; Any-Latin; NFC");
        }
        return useTransliterator;
    }
    
    @Override
    public String transliterate(String input) {
        return getTransliterator().transliterate(input);
    }
}
