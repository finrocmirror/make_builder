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

#ifndef __make_builder__enum_strings_builder_h__
#define __make_builder__enum_strings_builder_h__

//----------------------------------------------------------------------
// External includes (system with <>, local with "")
//----------------------------------------------------------------------
#include <vector>
#include <string>
#include <typeinfo>
#include <stdexcept>

//----------------------------------------------------------------------
// Debugging
//----------------------------------------------------------------------
#include <cassert>

//----------------------------------------------------------------------
// Namespace declaration
//----------------------------------------------------------------------
namespace make_builder
{

//----------------------------------------------------------------------
// Forward declarations / typedefs / enums
//----------------------------------------------------------------------
struct tEnumStrings;

namespace internal
{
const tEnumStrings &GetEnumStrings(const char *type_name);
}

struct tEnumStrings
{
  const char * const *strings;
  const size_t size;
};

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
  tRegisterEnumStrings(const char* type_name, const tEnumStrings &strings);
};

//----------------------------------------------------------------------
// Function declaration
//----------------------------------------------------------------------

/*!
 * \return Enum strings for this enum data type
 */
template <typename TEnum>
const tEnumStrings &GetEnumStrings()
{
  static const tEnumStrings &enum_strings(internal::GetEnumStrings(typeid(TEnum).name()));
  return enum_strings;
}

/*!
 * \param value enum constant
 * \return Enum string for this enum constant
 */
template <typename TEnum>
inline const char *GetEnumString(TEnum value)
{
  assert(static_cast<size_t>(value) < GetEnumStrings<TEnum>().size);
  return GetEnumStrings<TEnum>().strings[static_cast<size_t>(value)];
}

//template <typename TEnum>
//inline TEnum GetEnumValueFromString(const std::string &string)
//{
//  TEnum result;
//  const tEnumStrings &enum_strings(GetEnumStrings<TEnum>());
//  for (size_t i = 0; i < enum_strings.size; ++i)
//  {
//    if (string == enum_strings.strings[i])
//    {
//      result = static_cast<TEnum>(i);
//      break;
//    }
//  }
//
//  throw std::runtime_error("Could not find enum value for invalid string '" + string + "'!");
//
//  return result;
//}

//----------------------------------------------------------------------
// End of namespace declaration
//----------------------------------------------------------------------
}

#endif
