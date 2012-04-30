/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2011 Max Reichardt,
 *   Robotics Research Lab, University of Kaiserslautern
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
//----------------------------------------------------------------------
/*!\file    enum_strings.h
 *
 * \author  Max Reichardt
 *
 * \date    2011-08-24
 *
 * \brief Contains methods to retrieve auto-generated string constants
 *        for enum types
 */
//----------------------------------------------------------------------

#ifndef __make_builder__enum_strings_builder__h_
#define __make_builder__enum_strings_builder__h_

#include <vector>
#include <string>
#include <typeinfo>
#include <assert.h>

namespace make_builder
{
namespace internal
{

/*!
 * \param type_id Type id of enum type
 * \return Enum strings for this enum data type - or NULL if enum strings are unknown
 */
const std::vector<const char*>* GetEnumStrings(const char* type_id);

/*!
 * \param type_id Type id of enum type
 * \param strings Enum strings to register (should be terminated with null pointer)
 */
void RegisterEnumStrings(const char* type_id, const char* const* strings);

/*!
 * Helper struct to accelerate lookup
 */
template <typename ENUM>
struct tQuickLookup
{
  static const std::vector<const char*>* cached;
};

template <typename ENUM>
const std::vector<const char*>* tQuickLookup<ENUM>::cached = NULL;

}

/*!
 * \return Enum strings for this enum data type - or NULL if enum strings are unknown
 */
template<typename ENUM>
const std::vector<const char*>* GetEnumStrings()
{
  const std::vector<const char*>*& b = internal::tQuickLookup<ENUM>::cached;
  if (b == NULL)
  {
    b = internal::GetEnumStrings(typeid(ENUM).name()); // always produces same result, so concurrency is not an issue
  }
  return b;
}

/*!
 * \param value enum constant
 * \return Enum string for this enum constant - or NULL if enum strings are unknown
 */
template<typename ENUM>
const char* GetEnumString(ENUM value)
{
  const std::vector<const char*>* strings = GetEnumStrings<ENUM>();
  if (strings != NULL)
  {
    assert(static_cast<size_t>(value) < strings->size());
    return (*strings)[static_cast<size_t>(value)];
  }
  return NULL;
}

/*!
 * (Typically, only used in generated code)
 *
 * Utility struct to intialize enum strings in static context
 */
struct tRegisterEnumStrings
{
  /*!
   * \param type_name Normalized namespace and typename (normalized = without any template arguments)
   * \param strings Enum strings to register (should be terminated with null pointer)
   */
  tRegisterEnumStrings(const char* type_name, const char* const* strings)
  {
    internal::RegisterEnumStrings(type_name, strings);
  }
};

} // namespace

#endif

