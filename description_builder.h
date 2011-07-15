//
// You received this file as part of MCA2
// Modular Controller Architecture Version 2
//
//Copyright (C) Forschungszentrum Informatik Karlsruhe
//
//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// this is a -*- C++ -*- file
//----------------------------------------------------------------------
/*!\file    description_builder.h
 *
 * \author  Bernd-Helge Schaefer
 *
 * \brief   Contains the makro for generating string overlay arrays for enumerations
 *
 */
//----------------------------------------------------------------------

#ifndef __make_builder__description_builder__h_
#define __make_builder__description_builder__h_

//----------------------------------------------------------------------
// non MCA Includes - include with <>
// MCA Includes - include with ""
//----------------------------------------------------------------------
#include <cstddef>
#include <string>

//----------------------------------------------------------------------
// defines and consts
//----------------------------------------------------------------------
#ifndef QT_TR_NOOP
# define QT_TR_NOOP(x) (x)
#endif

#ifdef __WIN32__
# pragma warning(disable: 4002)
# define _DESCR_(storage,class,name,ignore,type) storage const char* name[]
#else
#define _DESCR_(storage,class,name,ignore,type) \
  storage const char* name[];
#endif

//----------------------------------------------------------------------
// Implementation
//----------------------------------------------------------------------
/*!
  This functions creates an enum value from a given string, if the
  string is one out of the specified description. In fact this
  function inverts the description builder.
 */
bool StringToEnum(const char *str, unsigned short &number, unsigned int number_of_descriptions, const char * const *descriptions);

#endif
